package com.intellij.lang.ant.config.execution;

import com.intellij.compiler.impl.javaCompiler.javac.JavacOutputParser;
import com.intellij.compiler.impl.javaCompiler.jikes.JikesOutputParser;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.lang.ant.AntBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.rt.ant.execution.AntMain2;
import com.intellij.rt.ant.execution.IdeaAntLogger2;
import com.intellij.util.text.StringTokenizer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class OutputParser implements BuildProgressWindow.BackgroundListener {

  @NonNls private static final String JAVAC = "javac";
  @NonNls private static final String ECHO = "echo";

  private static final Logger LOG = Logger.getInstance("#com.intellij.ant.execution.OutputParser");
  private final Project myProject;
  private final AntBuildMessageView myMessageView;
  private final WeakReference<BuildProgressWindow> myProgress;
  private final String myBuildName;
  private final OSProcessHandler myProcessHandler;
  private boolean isStopped;
  private List<String> myJavacMessages;
  private boolean myFirstLineProcessed;
  private boolean myStartedSuccessfully;
  private boolean myIsEcho;

  public OutputParser(Project project,
                      OSProcessHandler processHandler,
                      AntBuildMessageView errorsView,
                      BuildProgressWindow progress,
                      String buildName) {
    myProject = project;
    myProcessHandler = processHandler;
    myMessageView = errorsView;
    myProgress = new WeakReference<BuildProgressWindow>(progress);
    myBuildName = buildName;
    myMessageView.setParsingThread(this);
    if (progress != null) {
      progress.setBackgroundListener(this);
    }
  }

  public final void gotoBackground(String progressText, String progressStatistics) {
    setProgressText(progressText);
    setProgressStatistics(progressStatistics);
  }

  public final void stopProcess() {
    myProcessHandler.destroyProcess();
  }

  public boolean isTerminateInvoked() {
    return myProcessHandler.isProcessTerminating();
  }

  protected Project getProject() {
    return myProject;
  }

  protected OSProcessHandler getProcessHandler() {
    return myProcessHandler;
  }

  public final boolean isStopped() {
    return isStopped;
  }

  public final void setStopped(boolean stopped) {
    isStopped = stopped;
  }

  private void setProgressStatistics(String s) {
    final BuildProgressWindow progress = getProgress();
    if (progress != null) {
      progress.setStatistics(s);
    }
    if (progress == null || progress.isSentToBackground()) {
      myMessageView.setProgressStatistics(s);
    }
  }

  private void setProgressText(String s) {
    final BuildProgressWindow progress = getProgress();
    if (progress != null) {
      progress.setText(s);
    }
    if (progress == null || progress.isSentToBackground()) {
      myMessageView.setProgressText(s);
    }
  }

  private void printRawError(String text) {
    myMessageView.outputError(text, 0);
  }

  public final void readErrorOutput(String text) {
    if (!myFirstLineProcessed) {
      myFirstLineProcessed = true;
      myStartedSuccessfully = false;
      myMessageView.buildFailed(myBuildName);
    }
    if (!myStartedSuccessfully) {
      printRawError(text);
    }
  }


  protected final void processTag(char tagName, final String tagValue, final int priority) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.valueOf(tagName) + priority + "=" + tagValue);
    }

    if (IdeaAntLogger2.TARGET == tagName) {
      setProgressStatistics(AntBundle.message("target.tag.name.status.text", tagValue));
    }
    else if (IdeaAntLogger2.TASK == tagName) {
      setProgressText(AntBundle.message("executing.task.tag.value.status.text", tagValue));
      if (JAVAC.equals(tagValue)) {
        myJavacMessages = new ArrayList<String>();
      }
      else if (ECHO.equals(tagValue)) {
        myIsEcho = true;
      }
    }

    if (myJavacMessages != null && (IdeaAntLogger2.MESSAGE == tagName || IdeaAntLogger2.ERROR == tagName)) {
      myJavacMessages.add(tagValue);
      return;
    }

    if (IdeaAntLogger2.MESSAGE == tagName) {
      if (myIsEcho) {
        myMessageView.outputMessage(tagValue, AntMain2.MSG_VERBOSE);
      }
      else {
        myMessageView.outputMessage(tagValue, priority);
      }
    }
    else if (IdeaAntLogger2.TARGET == tagName) {
      myMessageView.startTarget(tagValue);
    }
    else if (IdeaAntLogger2.TASK == tagName) {
      myMessageView.startTask(tagValue);
    }
    else if (IdeaAntLogger2.ERROR == tagName) {
      myMessageView.outputError(tagValue, priority);
    }
    else if (IdeaAntLogger2.EXCEPTION == tagName) {
      String exceptionText = tagValue.replace(IdeaAntLogger2.EXCEPTION_LINE_SEPARATOR, '\n');
      myMessageView.outputException(exceptionText);
    }
    else if (IdeaAntLogger2.BUILD == tagName) {
      myMessageView.startBuild(myBuildName);
    }
    else if (IdeaAntLogger2.TARGET_END == tagName || IdeaAntLogger2.TASK_END == tagName) {
      final List<String> javacMessages = myJavacMessages;
      myJavacMessages = null;
      processJavacMessages(javacMessages, myMessageView, myProject);
      myIsEcho = false;
      if (IdeaAntLogger2.TARGET_END == tagName) {
        myMessageView.finishTarget();
      }
      else {
        myMessageView.finishTask();
      }
    }
  }

  private static boolean isJikesMessage(String errorMessage) {
    for (int j = 0; j < errorMessage.length(); j++) {
      if (errorMessage.charAt(j) == ':') {
        int offset = getNextTwoPoints(j, errorMessage);
        if (offset < 0) {
          continue;
        }
        offset = getNextTwoPoints(offset, errorMessage);
        if (offset < 0) {
          continue;
        }
        offset = getNextTwoPoints(offset, errorMessage);
        if (offset < 0) {
          continue;
        }
        offset = getNextTwoPoints(offset, errorMessage);
        if (offset >= 0) {
          return true;
        }
      }
    }
    return false;
  }

  private static int getNextTwoPoints(int offset, String message) {
    for (int i = offset + 1; i < message.length(); i++) {
      char c = message.charAt(i);
      if (c == ':') {
        return i;
      }
      if (Character.isDigit(c)) {
        continue;
      }
      return -1;
    }
    return -1;
  }

  private static void processJavacMessages(final List<String> javacMessages, final AntBuildMessageView messageView, Project project) {
    if (javacMessages == null) return;

    boolean isJikes = false;
    for (String errorMessage : javacMessages) {
      if (isJikesMessage(errorMessage)) {
        isJikes = true;
        break;
      }
    }

    com.intellij.compiler.OutputParser outputParser;
    if (isJikes) {
      outputParser = new JikesOutputParser(project);
    }
    else {
      outputParser = new JavacOutputParser(project);
    }

    com.intellij.compiler.OutputParser.Callback callback = new com.intellij.compiler.OutputParser.Callback() {
      private int myIndex = 0;

      @Nullable
      public String getCurrentLine() {
        if (javacMessages == null || myIndex >= javacMessages.size()) {
          return null;
        }
        return javacMessages.get(myIndex);
      }

      public String getNextLine() {
        return javacMessages.get(myIndex++);
      }

      public void message(final CompilerMessageCategory category,
                          final String message,
                          final String url,
                          final int lineNum,
                          final int columnNum) {
        StringTokenizer tokenizer = new StringTokenizer(message, "\n", false);
        final String[] strings = new String[tokenizer.countTokens()];
        //noinspection ForLoopThatDoesntUseLoopVariable
        for (int idx = 0; tokenizer.hasMoreTokens(); idx++) {
          strings[idx] = tokenizer.nextToken();
        }
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            VirtualFile file = url == null ? null : VirtualFileManager.getInstance().findFileByUrl(url);
            messageView.outputJavacMessage(convertCategory(category), strings, file, url, lineNum, columnNum);
          }
        });
      }

      public void setProgressText(String text) {
      }

      public void fileProcessed(String path) {
      }

      public void fileGenerated(String path) {
      }
    };
    try {
      while (true) {
        if (!outputParser.processMessageLine(callback)) {
          break;
        }
      }
    }
    catch (Exception e) {
      //ignore
    }
  }

  private static AntBuildMessageView.MessageType convertCategory(CompilerMessageCategory category) {
    if (CompilerMessageCategory.ERROR.equals(category)) {
      return AntBuildMessageView.MessageType.ERROR;
    }
    return AntBuildMessageView.MessageType.MESSAGE;
  }

  private BuildProgressWindow getProgress() {
    return myProgress.get();
  }
}
