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

import com.intellij.concurrency.JobScheduler;
import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineBuilder;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.junit.JUnitProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.util.ExecutionErrorDialog;
import com.intellij.history.LocalHistory;
import com.intellij.ide.macro.Macro;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildFileBase;
import com.intellij.lang.ant.config.AntBuildListener;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public final class ExecutionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ant.execution.ExecutionHandler");

  @NonNls public static final String PARSER_JAR = "xerces1.jar";

  private ExecutionHandler() {
  }

  /**
   * @param antBuildListener should not be null. Use {@link AntBuildListener#NULL}
   */
  public static void runBuild(final AntBuildFileBase buildFile,
                              String[] targets,
                              final AntBuildMessageView buildMessageViewToReuse,
                              final DataContext dataContext,
                              @NotNull final AntBuildListener antBuildListener) {
    FileDocumentManager.getInstance().saveAllDocuments();
    final AntCommandLineBuilder builder = new AntCommandLineBuilder();
    final AntBuildMessageView messageView;
    final GeneralCommandLine commandLine;
    synchronized (builder) {
      Project project = buildFile.getProject();

      try {
        builder.setBuildFile(buildFile.getAllOptions(), VfsUtil.virtualToIoFile(buildFile.getVirtualFile()));
        builder.calculateProperties(dataContext);
        builder.addTargets(targets);

        builder.getCommandLine().setCharset(EncodingProjectManager.getInstance(buildFile.getProject()).getDefaultCharset());

        messageView = prepareMessageView(buildMessageViewToReuse, buildFile, targets);
        commandLine = CommandLineBuilder.createFromJavaParameters(builder.getCommandLine());
        messageView.setBuildCommandLine(commandLine.getCommandLineString());
      }
      catch (RunCanceledException e) {
        e.showMessage(project, AntBundle.message("run.ant.erorr.dialog.title"));
        antBuildListener.buildFinished(AntBuildListener.FAILED_TO_RUN, 0);
        return;
      }
      catch (CantRunException e) {
        ExecutionErrorDialog.show(e, AntBundle.message("cant.run.ant.erorr.dialog.title"), project);
        antBuildListener.buildFinished(AntBuildListener.FAILED_TO_RUN, 0);
        return;
      }
      catch (Macro.ExecutionCancelledException e) {
        antBuildListener.buildFinished(AntBuildListener.ABORTED, 0);
        return;
      }
      catch (Throwable e) {
        antBuildListener.buildFinished(AntBuildListener.FAILED_TO_RUN, 0);
        LOG.error(e);
        return;
      }
    }

    final boolean startInBackground = buildFile.isRunInBackground();
    
    new Task.Backgroundable(null, AntBundle.message("ant.build.progress.dialog.title"), true) {

      public boolean shouldStartInBackground() {
        return startInBackground;
      }

      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          runBuild(indicator, messageView, buildFile, antBuildListener, commandLine);
        }
        catch (Throwable e) {
          LOG.error(e);
          antBuildListener.buildFinished(AntBuildListener.FAILED_TO_RUN, 0);
        }
      }
    }.queue();
  }

  private static void runBuild(final ProgressIndicator progress,
                               @NotNull final AntBuildMessageView errorView,
                               @NotNull final AntBuildFile buildFile,
                               @NotNull final AntBuildListener antBuildListener,
                               @NotNull GeneralCommandLine commandLine) {
    final Project project = buildFile.getProject();

    final long startTime = new Date().getTime();
    LocalHistory.getInstance().putSystemLabel(project, AntBundle.message("ant.build.local.history.label", buildFile.getName()));
    JUnitProcessHandler handler;
    try {
      handler = JUnitProcessHandler.runCommandLine(commandLine);
    }
    catch (final ExecutionException e) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          ExecutionErrorDialog.show(e, AntBundle.message("could.not.start.process.erorr.dialog.title"), project);
        }
      });
      antBuildListener.buildFinished(AntBuildListener.FAILED_TO_RUN, 0);
      return;
    }

    processRunningAnt(progress, handler, errorView, buildFile, startTime, antBuildListener);
    handler.waitFor();
  }

  private static void processRunningAnt(final ProgressIndicator progress,
                                        JUnitProcessHandler handler,
                                        final AntBuildMessageView errorView,
                                        final AntBuildFile buildFile,
                                        final long startTime,
                                        final AntBuildListener antBuildListener) {
    final Project project = buildFile.getProject();
    WindowManager.getInstance().getStatusBar(project).setInfo(AntBundle.message("ant.build.started.status.message"));

    final CheckCancelTask checkCancelTask = new CheckCancelTask(progress, handler);
    checkCancelTask.start(0);

    final OutputParser parser = OutputParser2.attachParser(project, handler, errorView, progress, buildFile);

    handler.addProcessListener(new ProcessAdapter() {
      public void processTerminated(ProcessEvent event) {
        checkCancelTask.cancel();
        parser.setStopped(true);
        errorView.stopScrollerThread();
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (project.isDisposed()) return;
            errorView.removeProgressPanel();
            ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.MESSAGES_WINDOW);
            if (toolWindow != null) { // can be null if project is closed
              toolWindow.activate(null, false);
              long buildTime = new Date().getTime() - startTime;
              errorView.buildFinished(progress != null && progress.isCanceled(), buildTime, antBuildListener);
            }
          }
        }, ModalityState.NON_MODAL);
      }
    });
    handler.startNotify();
    errorView.startScrollerThread();
  }

  static final class CheckCancelTask implements Runnable {
    private final ProgressIndicator myProgressIndicator;
    private final OSProcessHandler myProcessHandler;
    private volatile boolean myCanceled;

    public CheckCancelTask(ProgressIndicator progressIndicator, OSProcessHandler process) {
      myProgressIndicator = progressIndicator;
      myProcessHandler = process;
    }

    public void cancel() {
      myCanceled = true;
    }

    public void run() {
      if (!myCanceled) {
        try {
          myProgressIndicator.checkCanceled();
          start(50);
        }
        catch (ProcessCanceledException e) {
          myProcessHandler.destroyProcess();
        }
      }
    }

    public void start(final long delay) {
      JobScheduler.getScheduler().schedule(this, delay, TimeUnit.MILLISECONDS);
    }
  }

  private static AntBuildMessageView prepareMessageView(AntBuildMessageView buildMessageViewToReuse,
                                                        AntBuildFileBase buildFile,
                                                        String[] targets) throws RunCanceledException {
    AntBuildMessageView messageView;
    if (buildMessageViewToReuse != null) {
      messageView = buildMessageViewToReuse;
      messageView.emptyAll();
    }
    else {
      messageView = AntBuildMessageView.openBuildMessageView(buildFile.getProject(), buildFile, targets);
      if (messageView == null) {
        throw new RunCanceledException(AntBundle.message("canceled.by.user.error.message"));
      }
    }
    return messageView;
  }
}
