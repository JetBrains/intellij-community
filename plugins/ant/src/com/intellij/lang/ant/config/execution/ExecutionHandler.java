// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.execution;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaCommandLineState;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.process.*;
import com.intellij.execution.target.*;
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest;
import com.intellij.execution.testframework.Printable;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.util.ExecutionErrorDialog;
import com.intellij.execution.wsl.target.WslTargetEnvironmentConfiguration;
import com.intellij.execution.wsl.target.WslTargetEnvironmentRequest;
import com.intellij.history.LocalHistory;
import com.intellij.ide.macro.Macro;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildFileBase;
import com.intellij.lang.ant.config.AntBuildListener;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.impl.BuildFileProperty;
import com.intellij.lang.ant.segments.OutputPacketProcessor;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class ExecutionHandler {
  private static final Logger LOG = Logger.getInstance(ExecutionHandler.class);

  @NonNls public static final String PARSER_JAR = "xerces1.jar";

  private ExecutionHandler() {
  }

  @Nullable
  public static ProcessHandler executeRunConfiguration(AntRunConfiguration antRunConfiguration,
                                                       final DataContext dataContext,
                                                       List<BuildFileProperty> additionalProperties,
                                                       @NotNull final AntBuildListener antBuildListener) {
    AntBuildTarget target = antRunConfiguration.getTarget();
    if (target == null) {
      return null;
    }
    Future<ProcessHandler> result = runBuildImpl(
      (AntBuildFileBase)target.getModel().getBuildFile(), target.getTargetNames(), null, dataContext, additionalProperties, antBuildListener, false
    );
    if (result != null) {
      try {
        return result.get();
      }
      catch (InterruptedException | java.util.concurrent.ExecutionException e) {
        LOG.warn(e);
      }
    }
    return null;
  }


  @Deprecated(forRemoval = true)
  public static void runBuild(final AntBuildFileBase buildFile,
                              String[] targets,
                              @Nullable final AntBuildMessageView buildMessageViewToReuse,
                              final DataContext dataContext,
                              List<BuildFileProperty> additionalProperties, @NotNull final AntBuildListener antBuildListener) {
    runBuild(buildFile, Arrays.asList(targets), buildMessageViewToReuse, dataContext, additionalProperties, antBuildListener);
  }

  /**
   * @param antBuildListener should not be null. Use {@link AntBuildListener#NULL}
   */
  public static void runBuild(final AntBuildFileBase buildFile,
                              List<@NlsSafe String> targets,
                              @Nullable final AntBuildMessageView buildMessageViewToReuse,
                              final DataContext dataContext,
                              List<BuildFileProperty> additionalProperties, @NotNull final AntBuildListener antBuildListener) {
      runBuildImpl(buildFile, targets, buildMessageViewToReuse, dataContext, additionalProperties, antBuildListener, true);
  }

  /**
   * @param antBuildListener should not be null. Use {@link AntBuildListener#NULL}
   */
  @Nullable
  private static Future<ProcessHandler> runBuildImpl(final AntBuildFileBase buildFile,
                                                     List<@NlsSafe String> targets,
                                                     @Nullable final AntBuildMessageView buildMessageViewToReuse,
                                                     final DataContext dataContext,
                                                     List<BuildFileProperty> additionalProperties,
                                                     @NotNull final AntBuildListener antBuildListener, final boolean waitFor) {
    final AntBuildMessageView messageView;
    final TargetEnvironmentRequest request;
    final SimpleJavaParameters javaParameters;
    final AntBuildListenerWrapper listenerWrapper = new AntBuildListenerWrapper(buildFile, antBuildListener);
    final Project project = buildFile.getProject();
    try {
      FileDocumentManager.getInstance().saveAllDocuments();
      final AntCommandLineBuilder builder = new AntCommandLineBuilder();

      builder.setBuildFile(buildFile.getAllOptions(), VfsUtilCore.virtualToIoFile(buildFile.getVirtualFile()));
      builder.calculateProperties(dataContext, buildFile.getProject(), additionalProperties);
      builder.addTargets(targets);

      builder.getCommandLine().setCharset(EncodingProjectManager.getInstance(buildFile.getProject()).getDefaultCharset());

      messageView = prepareMessageView(buildMessageViewToReuse, buildFile, targets, additionalProperties);
      javaParameters = builder.getCommandLine();

      WslTargetEnvironmentConfiguration wslConfiguration = JavaCommandLineState.checkCreateWslConfiguration(javaParameters.getJdk());
      if (wslConfiguration != null) {
        request = new WslTargetEnvironmentRequest(wslConfiguration);
      }
      else {
        request = new LocalTargetEnvironmentRequest();
      }

      project.getMessageBus().syncPublisher(AntExecutionListener.TOPIC).beforeExecution(new AntBeforeExecutionEvent(buildFile, messageView));
    }
    catch (RunCanceledException e) {
      e.showMessage(project, AntBundle.message("run.ant.error.dialog.title"));
      listenerWrapper.buildFinished(AntBuildListener.FAILED_TO_RUN, 0);
      return null;
    }
    catch (CantRunException e) {
      ExecutionErrorDialog.show(e, AntBundle.message("cant.run.ant.error.dialog.title"), project);
      listenerWrapper.buildFinished(AntBuildListener.FAILED_TO_RUN, 0);
      return null;
    }
    catch (Macro.ExecutionCancelledException e) {
      listenerWrapper.buildFinished(AntBuildListener.ABORTED, 0);
      return null;
    }
    catch (Throwable e) {
      listenerWrapper.buildFinished(AntBuildListener.FAILED_TO_RUN, 0);
      LOG.error(e);
      return null;
    }
    CompletableFuture<ProcessHandler> future = new CompletableFuture<>();
    new Task.Backgroundable(buildFile.getProject(), AntBundle.message("ant.build.progress.dialog.title"), true) {

      @Override
      public void onCancel() {
        listenerWrapper.buildFinished(AntBuildListener.ABORTED, 0);
      }

      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          TargetedCommandLineBuilder builder = javaParameters.toCommandLine(request);
          TargetEnvironment environment = request.prepareEnvironment(TargetProgressIndicator.EMPTY);
          TargetedCommandLine commandLine = builder.build();

          messageView.setBuildCommandLine(commandLine.getCommandPresentation(environment));

          ProcessHandler handler = runBuild(indicator, messageView, buildFile, listenerWrapper, commandLine, environment);
          future.complete(handler);
          if (waitFor && handler != null) {
            handler.waitFor();
          }
        }
        catch (Throwable e) {
          LOG.error(e);
          listenerWrapper.buildFinished(AntBuildListener.FAILED_TO_RUN, 0);
        }
      }
    }.queue();
    return future;
  }

  @Nullable
  private static ProcessHandler runBuild(@NotNull final ProgressIndicator progress,
                                         @NotNull final AntBuildMessageView errorView,
                                         @NotNull final AntBuildFileBase buildFile,
                                         @NotNull final AntBuildListener antBuildListener,
                                         @NotNull TargetedCommandLine commandLine,
                                         @NotNull TargetEnvironment targetEnvironment) {
    final Project project = buildFile.getProject();

    final long startTime = System.currentTimeMillis();
    LocalHistory.getInstance().putSystemLabel(project, AntBundle.message("ant.build.local.history.label", buildFile.getName()));
    final AntProcessHandler handler;
    try {
      handler = AntProcessHandler.runCommandLine(commandLine, targetEnvironment, progress);
    }
    catch (final ExecutionException e) {
      ApplicationManager.getApplication().invokeLater(
        () -> ExecutionErrorDialog.show(e, AntBundle.message("could.not.start.process.error.dialog.title"), project));
      antBuildListener.buildFinished(AntBuildListener.FAILED_TO_RUN, 0);
      return null;
    }

    processRunningAnt(progress, handler, errorView, buildFile, startTime, antBuildListener);
    return handler;
  }

  private static void processRunningAnt(final ProgressIndicator progress,
                                        final AntProcessHandler handler,
                                        final AntBuildMessageView errorView,
                                        final AntBuildFileBase buildFile,
                                        final long startTime,
                                        final AntBuildListener antBuildListener) {
    final Project project = buildFile.getProject();
    final StatusBar statusbar = WindowManager.getInstance().getStatusBar(project);
    if (statusbar != null) {
      statusbar.setInfo(AntBundle.message("ant.build.started.status.message"));
    }

    final CheckCancelTask checkCancelTask = new CheckCancelTask(progress, handler);
    checkCancelTask.start(0);

    final OutputParser parser = OutputParser2.attachParser(project, handler, errorView, progress, buildFile);

    handler.putUserData(AntRunProfileState.MESSAGE_VIEW, errorView);
    handler.addProcessListener(new ProcessAdapter() {
      private final @NlsSafe StringBuilder myUnprocessedStdErr = new StringBuilder();

      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        if (outputType == ProcessOutputTypes.STDERR) {
          final String text = event.getText();
          synchronized (myUnprocessedStdErr) {
            myUnprocessedStdErr.append(text);
          }
        }
      }

      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        final long buildTime = System.currentTimeMillis() - startTime;
        checkCancelTask.cancel();
        parser.setStopped(true);

        final OutputPacketProcessor dispatcher = handler.getErr().getEventsDispatcher();

        try {
          if (event.getExitCode() != 0) {
            // in case process exits abnormally, provide all unprocessed stderr content
            final String unprocessed;
            synchronized (myUnprocessedStdErr) {
              unprocessed = myUnprocessedStdErr.toString();
              myUnprocessedStdErr.setLength(0);
            }
            if (!unprocessed.isEmpty()) {
              dispatcher.processOutput(new Printable() {
                @Override
                public void printOn(Printer printer) {
                  errorView.outputError(unprocessed, AntBuildMessageView.PRIORITY_ERR);
                }
              });
            }
          }
          else {
            synchronized (myUnprocessedStdErr) {
              myUnprocessedStdErr.setLength(0);
            }
          }
        }
        finally {
          errorView.buildFinished(progress != null && progress.isCanceled(), buildTime, antBuildListener, dispatcher);
        }
      }
    });
    handler.startNotify();
  }

  static final class CheckCancelTask implements Runnable {
    private final ProgressIndicator myProgressIndicator;
    private final OSProcessHandler myProcessHandler;
    private volatile boolean myCanceled;

    CheckCancelTask(ProgressIndicator progressIndicator, OSProcessHandler process) {
      myProgressIndicator = progressIndicator;
      myProcessHandler = process;
    }

    public void cancel() {
      myCanceled = true;
    }

    @Override
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
      AppExecutorUtil.getAppScheduledExecutorService().schedule(this, delay, TimeUnit.MILLISECONDS);
    }
  }

  private static AntBuildMessageView prepareMessageView(@Nullable AntBuildMessageView buildMessageViewToReuse,
                                                        AntBuildFileBase buildFile,
                                                        List<String> targets, List<BuildFileProperty> additionalProperties) throws RunCanceledException {
    AntBuildMessageView messageView;
    if (buildMessageViewToReuse != null) {
      messageView = buildMessageViewToReuse;
      messageView.emptyAll();
    }
    else {
      messageView = AntBuildMessageView.openBuildMessageView(buildFile.getProject(), buildFile, targets, additionalProperties);
      if (messageView == null) {
        throw new RunCanceledException(AntBundle.message("canceled.by.user.error.message"));
      }
    }
    return messageView;
  }

  private static class AntBuildListenerWrapper implements AntBuildListener {
    @NotNull
    private final AntBuildFile myBuildFile;
    @NotNull
    private final AntBuildListener myDelegate;

    AntBuildListenerWrapper(@NotNull AntBuildFile buildFile, @NotNull AntBuildListener delegate) {
      myBuildFile = buildFile;
      myDelegate = delegate;
    }

    @Override
    public void buildFinished(int state, int errorCount) {
      try {
        final AntFinishedExecutionEvent.Status status = state == AntBuildListener.ABORTED? AntFinishedExecutionEvent.Status.CANCELED :
                                                  state == AntBuildListener.FAILED_TO_RUN? AntFinishedExecutionEvent.Status.FAILURE :
                                                  AntFinishedExecutionEvent.Status.SUCCESS;
        myBuildFile.getProject().getMessageBus().syncPublisher(AntExecutionListener.TOPIC).buildFinished(
          new AntFinishedExecutionEvent(myBuildFile, status, errorCount)
        );
      }
      finally {
        myDelegate.buildFinished(state, errorCount);
      }
    }
  }
}
