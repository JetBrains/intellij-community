package com.intellij.lang.ant.config.execution;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionUtil;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.junit.JUnitProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.Date;

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
                              final AntBuildListener antBuildListener) {
    FileDocumentManager.getInstance().saveAllDocuments();
    LOG.assertTrue(antBuildListener != null);
    final AntCommandLineBuilder builder = new AntCommandLineBuilder();
    final BuildProgressWindow progress;
    final AntBuildMessageView messageView;
    final GeneralCommandLine commandLine;
    synchronized (builder) {
      Project project = buildFile.getProject();
      progress = !buildFile.isRunInBackground() ? new BuildProgressWindow(project) : null;

      try {
        builder.setBuildFile(buildFile.getAllOptions(), VfsUtil.virtualToIoFile(buildFile.getVirtualFile()));
        builder.calculateProperties(dataContext);
        builder.addTargets(targets);
        messageView = prepareMessageView(buildMessageViewToReuse, buildFile, targets);
        commandLine = GeneralCommandLine.createFromJavaParameters(builder.getCommandLine());
        messageView.setBuildCommandLine(commandLine.getCommandLineString());
        if (progress != null) progress.start();
      }
      catch (RunCanceledException e) {
        e.showMessage(project, AntBundle.message("run.ant.erorr.dialog.title"));
        antBuildListener.buildFinished(AntBuildListener.FAILED_TO_RUN, 0);
        return;
      }
      catch (CantRunException e) {
        ExecutionUtil.showExecutionErrorMessage(e, AntBundle.message("cant.run.ant.erorr.dialog.title"), project);
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
    @NonNls final String threadName = "Ant build";
    Thread thread = new Thread(new Runnable() {
      public void run() {
        synchronized (builder) {
          try {
            runBuild(progress, messageView, buildFile, antBuildListener, commandLine);
          }
          catch (Throwable e) {
            LOG.error(e);
            antBuildListener.buildFinished(AntBuildListener.FAILED_TO_RUN, 0);
          }
        }
      }
    }, threadName);
    thread.start();
  }

  private static void runBuild(final BuildProgressWindow progress,
                               final AntBuildMessageView errorView,
                               final AntBuildFile buildFile,
                               final AntBuildListener antBuildListener,
                               GeneralCommandLine commandLine) {
    LOG.assertTrue(antBuildListener != null);
    LOG.assertTrue(errorView != null);
    LOG.assertTrue(commandLine != null);
    LOG.assertTrue(buildFile != null);
    final Project project = buildFile.getProject();
    final File[] workingDirectory = new File[1];
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        VirtualFile vFile = buildFile.getVirtualFile();
        if (vFile == null || !vFile.isValid()) return;
        VirtualFile vDir = vFile.getParent();
        workingDirectory[0] = VfsUtil.virtualToIoFile(vDir);
      }
    });

    final long startTime = new Date().getTime();
    JUnitProcessHandler handler;
    try {
      handler = JUnitProcessHandler.runCommandLine(commandLine);
    }
    catch (final ExecutionException e) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          ExecutionUtil.showExecutionErrorMessage(e, AntBundle.message("could.not.start.process.erorr.dialog.title"), project);
        }
      });
      antBuildListener.buildFinished(AntBuildListener.FAILED_TO_RUN, 0);
      return;
    }

    processRunningAnt(progress, handler, errorView, buildFile, startTime, antBuildListener);

  }

  private static void processRunningAnt(final BuildProgressWindow progress,
                                        JUnitProcessHandler handler,
                                        final AntBuildMessageView errorView,
                                        final AntBuildFile buildFile,
                                        final long startTime,
                                        final AntBuildListener antBuildListener) {
    final Project project = buildFile.getProject();
    WindowManager.getInstance().getStatusBar(project).setInfo(AntBundle.message("ant.build.started.status.message"));

    final CheckCancelThread checkThread = new CheckCancelThread(progress, handler);
    checkThread.start();

    final OutputParser parser = OutputParser2.attachParser(project, handler, errorView, progress, buildFile);

    handler.addProcessListener(new ProcessAdapter() {
      public void processTerminated(ProcessEvent event) {
        checkThread.cancel();
        parser.setStopped(true);
        if (progress != null) {
          progress.stop();
        }
        errorView.stopScrollerThread();
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (project.isDisposed()) return;
            errorView.removeProgressPanel();
            ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.MESSAGES_WINDOW);
            if (toolWindow != null) { // can be null if project is closed
              toolWindow.activate(null);
              long buildTime = new Date().getTime() - startTime;
              errorView.buildFinished(progress != null && progress.isCanceled(), buildTime, antBuildListener);
            }
          }
        }, ModalityState.NON_MMODAL);
      }
    });
    handler.startNotify();
    errorView.startScrollerThread();
  }

  static final class CheckCancelThread extends Thread {
    private final BuildProgressWindow myProgressWindow;
    private final OSProcessHandler myProcessHandler;
    private boolean myCanceled;

    public CheckCancelThread(BuildProgressWindow progressWindow, OSProcessHandler process) {
      myProgressWindow = progressWindow;
      myProcessHandler = process;
    }

    public void cancel() {
      myCanceled = true;
    }

    public void run() {
      while (true) {
        if (myCanceled) return;
        if (myProgressWindow != null && myProgressWindow.isCanceled()) {
          myProcessHandler.destroyProcess();
          return;
        }
        try {
          Thread.sleep(50);
        }
        catch (InterruptedException e) {
          // ignore
        }
      }
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
      if (messageView == null) throw new RunCanceledException(AntBundle.message("canceled.by.user.error.message"));
    }
    return messageView;
  }
}
