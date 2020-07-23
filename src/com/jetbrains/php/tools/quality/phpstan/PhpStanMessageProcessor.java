package com.jetbrains.php.tools.quality.phpstan;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.jetbrains.php.tools.quality.QualityToolAnnotatorInfo;
import com.jetbrains.php.tools.quality.QualityToolMessage;
import com.jetbrains.php.tools.quality.QualityToolType;
import com.jetbrains.php.tools.quality.QualityToolXmlMessageProcessor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.jetbrains.php.tools.quality.QualityToolMessage.Severity.ERROR;
import static com.jetbrains.php.tools.quality.QualityToolMessage.Severity.WARNING;
import static com.jetbrains.php.tools.quality.phpstan.PhpStanGlobalInspection.PHPSTAN_ANNOTATOR_INFO;

public class PhpStanMessageProcessor extends QualityToolXmlMessageProcessor {
  private final static String ERROR_TAG = "error";
  private final static String FILE_TAG = "file";
  @NonNls private final static String WARNING_MESSAGE_START = "<file";
  @NonNls private final static String WARNING_MESSAGE_END = "</file>";
  private final static String WARNING_TAG = "warning";
  @NonNls private final static String LINE_NUMBER_ATTR = "line";
  @NonNls private final static String COLUMN_NUMBER_ATTR = "column";
  private final static String MESSAGE_ATTR = "message";
  @NonNls private final static String SEVERITY_ATTR = "severity";
  private final static String FILE_NAME_ATTR = "name";
  private final Set<PhpStanXmlMessageHandler.ProblemDescription> lineMessages = new HashSet<>();
  private final HighlightDisplayLevel myWarningsHighlightLevel;
  final String myFilePath;
  final PsiFile myPsiFile;
  final Project myProject;

  protected PhpStanMessageProcessor(QualityToolAnnotatorInfo info) {
    super(info);
    myWarningsHighlightLevel = HighlightDisplayLevel.WARNING; // TODO: fix
    myFilePath = info.getOriginalFile() != null ? info.getOriginalFile().getPath(): null;
    myPsiFile = info.getPsiFile();
    myProject = info.getProject();
  }

  @Override
  protected void processMessage(InputSource source) throws SAXException, IOException {
    PhpStanXmlMessageHandler messageHandler = (PhpStanXmlMessageHandler)getXmlMessageHandler(myFilePath);
    mySAXParser.parse(source, messageHandler);
    if (messageHandler.isStatusValid()) {
      if (myPsiFile != null) {
        for (PhpStanXmlMessageHandler.ProblemDescription problem : messageHandler.getProblemList()) {
          final Document document = PsiDocumentManager.getInstance(myPsiFile.getProject()).getDocument(myPsiFile);
          QualityToolMessage qualityToolMessage;
          if (document != null && problem.getLineNumber() - 1 > 0 && problem.getLineNumber() - 1 < document.getLineCount()) {
            qualityToolMessage = new QualityToolMessage(this, TextRange
              .create(document.getLineStartOffset(problem.getLineNumber() - 1) + problem.getColumn(),
                      document.getLineEndOffset(problem.getLineNumber() - 1)), problem.getSeverity(), problem.getMessage());
          }
          else {
            qualityToolMessage = new QualityToolMessage(this, problem.getLineNumber(), problem.getSeverity(), problem.getMessage());
          }
          if (lineMessages.add(problem)) {
            addMessage(qualityToolMessage);
          }
        }
      } else {
        myProject.putUserData(PHPSTAN_ANNOTATOR_INFO, messageHandler.getProblemList());
      }
    }
  }

  @Override
  protected XMLMessageHandler getXmlMessageHandler() {
    return null;
  }

  protected XMLMessageHandler getXmlMessageHandler(@Nullable String filePath) {
    return new PhpStanXmlMessageHandler(filePath);
  }

  @Override
  public int getMessageStart(@NotNull String line) {
    return line.indexOf(WARNING_MESSAGE_START);
  }

  @Override
  public int getMessageEnd(@NotNull String line) {
    return line.indexOf(WARNING_MESSAGE_END);
  }

  @NonNls
  @Nullable
  @Override
  protected String getMessagePrefix() {
    return "phpstan";
  }

  @Nullable
  @Override
  protected HighlightDisplayLevel severityToDisplayLevel(@NotNull QualityToolMessage.Severity severity) {
    return WARNING.equals(severity) ? myWarningsHighlightLevel : null;
  }

  @Override
  protected QualityToolType getQualityToolType() {
    return PhpStanQualityToolType.INSTANCE;
  }

  @Override
  public boolean processStdErrMessages() {
    return false;
  }

  static final class PhpStanXmlMessageHandler extends XMLMessageHandler {

    private final String myFilePath;
    private String myFileAttr;

    private PhpStanXmlMessageHandler(@Nullable String filePath) {
      myFilePath = filePath;
    }

    private List<ProblemDescription> myProblemList;

    private List<ProblemDescription> getProblemList() {
      return myProblemList;
    }

    @Override
    protected void parseTag(@NotNull String tagName, @NotNull Attributes attributes) {
      if (FILE_TAG.equals(tagName)) {
        myFileAttr = attributes.getValue(FILE_NAME_ATTR);
        myProblemList = myFilePath == null || myFilePath.endsWith(myFileAttr) ? new ArrayList<>() : null;
      }
      else if (ERROR_TAG.equals(tagName) | WARNING_TAG.equals(tagName)) {
        if (myProblemList != null) {
          mySeverity = attributes.getValue(SEVERITY_ATTR).equals(ERROR_TAG) ? ERROR : WARNING;
          myLineNumber = parseLineNumber(attributes.getValue(LINE_NUMBER_ATTR));
          int column = parseLineNumber(attributes.getValue(COLUMN_NUMBER_ATTR));
          myProblemList.add(new ProblemDescription(mySeverity, myLineNumber, column, attributes.getValue(MESSAGE_ATTR), myFileAttr));
        }
      }
    }
    
    static class ProblemDescription{
      ProblemDescription(QualityToolMessage.Severity severity, int lineNumber, int column, String message, String file) {
        mySeverity = severity;
        myLineNumber = lineNumber;
        myColumn = column;
        myMessage = message;
        myFile = file;
      }

      private final QualityToolMessage.Severity mySeverity;
      private final int myLineNumber;
      private final int myColumn;
      private final String myMessage;
      private final String myFile;

      QualityToolMessage.Severity getSeverity() {
        return mySeverity;
      }

      int getLineNumber() {
        return myLineNumber;
      }

      int getColumn() {
        return myColumn;
      }

      String getMessage() {
        return myMessage;
      }

      String getFile() {
        return myFile;
      }
    }
  }
}