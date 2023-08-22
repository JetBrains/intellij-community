/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.ant.config.execution;

import com.intellij.compiler.impl.javaCompiler.javac.JavacOutputParser;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.lang.ant.AntBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.problems.Problem;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.rt.ant.execution.IdeaAntLogger2;
import com.intellij.util.text.StringTokenizer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OutputParser{

  private static final @NonNls String JAVAC = "javac";

  private static final Logger LOG = Logger.getInstance(OutputParser.class);
  private final Project myProject;
  private final AntBuildMessageView myMessageView;
  private final WeakReference<ProgressIndicator> myProgress;
  private final @Nls String myBuildName;
  private final OSProcessHandler myProcessHandler;
  private volatile boolean isStopped;
  private List<String> myJavacMessages;
  private boolean myFirstLineProcessed;
  private boolean myStartedSuccessfully;

  public OutputParser(Project project,
                      OSProcessHandler processHandler,
                      AntBuildMessageView errorsView,
                      ProgressIndicator progress,
                      @Nls String buildName) {
    myProject = project;
    myProcessHandler = processHandler;
    myMessageView = errorsView;
    myProgress = new WeakReference<>(progress);
    myBuildName = buildName;
    myMessageView.setParsingThread(this);
  }

  public final void stopProcess() {
    myProcessHandler.destroyProcess();
  }

  public boolean isTerminateInvoked() {
    return myProcessHandler.isProcessTerminating() || myProcessHandler.isProcessTerminated();
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

  private void setProgressStatistics(@NlsContexts.ProgressText String s) {
    final ProgressIndicator progress = myProgress.get();
    if (progress != null) {
      progress.setText2(s);
    }
  }

  private void setProgressText(@NlsContexts.ProgressText String s) {
    final ProgressIndicator progress = myProgress.get();
    if (progress != null) {
      progress.setText(s);
    }
  }

  private void printRawError(@Nls String text) {
    myMessageView.outputError(text, AntBuildMessageView.PRIORITY_ERR);
  }

  public final void readErrorOutput(@NlsSafe String text) {
    if (!myFirstLineProcessed) {
      myFirstLineProcessed = true;
      myStartedSuccessfully = false;
      myMessageView.buildFailed(myBuildName);
    }
    if (!myStartedSuccessfully) {
      printRawError(text);
    }
  }


  protected final void processTag(char tagName, @NlsSafe final String tagValue, @AntMessage.Priority int priority) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.valueOf(tagName) + priority + "=" + tagValue);
    }

    if (IdeaAntLogger2.TARGET == tagName) {
      setProgressStatistics(AntBundle.message("target.tag.name.status.text", tagValue));
    }
    else if (IdeaAntLogger2.TASK == tagName) {
      setProgressText(AntBundle.message("executing.task.tag.value.status.text", tagValue));
      if (JAVAC.equals(tagValue)) {
        myJavacMessages = new ArrayList<>();
      }
    }

    if (myJavacMessages != null && (IdeaAntLogger2.MESSAGE == tagName || IdeaAntLogger2.ERROR == tagName)) {
      myJavacMessages.add(tagValue);
      return;
    }

    if (IdeaAntLogger2.MESSAGE == tagName) {
      myMessageView.outputMessage(tagValue, priority);
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
      if (IdeaAntLogger2.TARGET_END == tagName) {
        myMessageView.finishTarget();
      }
      else {
        myMessageView.finishTask();
      }
    }
  }

  @AntMessage.Priority
  static int fixPriority(int priority) {
    if (priority == AntBuildMessageView.PRIORITY_ERR ||
        priority == AntBuildMessageView.PRIORITY_WARN ||
        priority == AntBuildMessageView.PRIORITY_INFO ||
        priority == AntBuildMessageView.PRIORITY_VERBOSE ||
        priority == AntBuildMessageView.PRIORITY_DEBUG) {
      return priority;
    }
    return AntBuildMessageView.PRIORITY_VERBOSE; // fallback value for unknown priority value
  }

  private static void processJavacMessages(final List<String> javacMessages, final AntBuildMessageView messageView, final Project project) {
    if (javacMessages == null) {
      return;
    }

    final com.intellij.compiler.OutputParser outputParser = new JavacOutputParser(project);

    com.intellij.compiler.OutputParser.Callback callback = new com.intellij.compiler.OutputParser.Callback() {
      private int myIndex = -1;

      @Override
      @Nullable
      public String getCurrentLine() {
        if (myIndex >= javacMessages.size()) {
          return null;
        }
        return javacMessages.get(myIndex);
      }

      @Override
      public String getNextLine() {
        final int size = javacMessages.size();
        final int next = Math.min(myIndex + 1, javacMessages.size());
        myIndex = next;
        if (next >= size) {
          return null;
        }
        return javacMessages.get(next);
      }

      @Override
      public void pushBack(String line) {
        myIndex--;
      }

      @Override
      public void message(final CompilerMessageCategory category,
                          final String message,
                          final String url,
                          final int lineNum,
                          final int columnNum) {
        StringTokenizer tokenizer = new StringTokenizer(message, "\n", false);
        final String[] strings = new String[tokenizer.countTokens()];
        for (int idx = 0; tokenizer.hasMoreTokens(); idx++) {
          strings[idx] = tokenizer.nextToken();
        }
        ApplicationManager.getApplication().runReadAction(() -> {
          VirtualFile file = url == null ? null : VirtualFileManager.getInstance().findFileByUrl(url);
          messageView.outputJavacMessage(convertCategory(category), strings, file, url, lineNum, columnNum);

          if (file != null && category == CompilerMessageCategory.ERROR) {
            final WolfTheProblemSolver wolf = WolfTheProblemSolver.getInstance(project);
            final Problem problem = wolf.convertToProblem(file, lineNum, columnNum, strings);
            wolf.weHaveGotNonIgnorableProblems(file, Collections.singletonList(problem));
          }
        });
      }

      @Override
      public void setProgressText(String text) {
      }

      @Override
      public void fileProcessed(String path) {
      }

      @Override
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

}
