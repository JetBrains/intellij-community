/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package git4idea.commands;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import git4idea.GitVcs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * All Git commands are cancellable when called via {@link GitHandler}. <br/>
 * To execute the command synchronously, call {@link Git#runCommand(Computable)}.<br/>
 * To execute in the background or under a modal progress, use the standard {@link Task}. <br/>
 * To watch the progress, call {@link GitStandardProgressAnalyzer#createListener(ProgressIndicator)}.
 *
 * @deprecated To remove in IDEA 2017.
 */
@Deprecated
public class GitTask {

  private static final Logger LOG = Logger.getInstance(GitTask.class);

  private final Project myProject;
  private final GitHandler myHandler;
  private final String myTitle;
  private GitProgressAnalyzer myProgressAnalyzer;
  private ProgressIndicator myProgressIndicator;

  public GitTask(Project project, GitHandler handler, String title) {
    myProject = project;
    myHandler = handler;
    myTitle = title;
  }

  /**
   * Executes this task synchronously, with a modal progress dialog.
   * @return Result of the task execution.
   */
  @SuppressWarnings("unused")
  public GitTaskResult executeModal() {
    return execute(true);
  }

  /**
   * Executes this task asynchronously, in background. Calls the resultHandler when finished.
   * @param resultHandler callback called after the task has finished or was cancelled by user or automatically.
   */
  public void executeAsync(final GitTaskResultHandler resultHandler) {
    execute(false, false, resultHandler);
  }

  public void executeInBackground(boolean sync, final GitTaskResultHandler resultHandler) {
    execute(sync, false, resultHandler);
  }

  // this is always sync
  @NotNull
  public GitTaskResult execute(boolean modal) {
    final AtomicReference<GitTaskResult> result = new AtomicReference<>(GitTaskResult.INITIAL);
    execute(true, modal, new GitTaskResultHandlerAdapter() {
      @Override
      protected void run(GitTaskResult res) {
        result.set(res);
      }
    });
    return result.get();
  }

  /**
   * The most general execution method.
   * @param sync  Set to {@code true} to make the calling thread wait for the task execution.
   * @param modal If {@code true}, the task will be modal with a modal progress dialog. If false, the task will be executed in
   * background. {@code modal} implies {@code sync}, i.e. if modal then sync doesn't matter: you'll wait anyway.
   * @param resultHandler Handle the result.
   * @see #execute(boolean)
   */
  public void execute(boolean sync, boolean modal, final GitTaskResultHandler resultHandler) {
    final Object LOCK = new Object();
    final AtomicBoolean completed = new AtomicBoolean();

    if (modal) {
      final ModalTask task = new ModalTask(myProject, myHandler, myTitle) {
        @Override public void onSuccess() {
          commonOnSuccess(LOCK, resultHandler);
          completed.set(true);
        }
        @Override public void onCancel() {
          commonOnCancel(LOCK, resultHandler);
          completed.set(true);
        }
        @Override public void onThrowable(@NotNull Throwable error) {
          super.onThrowable(error);
          commonOnCancel(LOCK, resultHandler);
          completed.set(true);
        }
      };
      ApplicationManager.getApplication().invokeAndWait(() -> ProgressManager.getInstance().run(task));
    } else {
      final BackgroundableTask task = new BackgroundableTask(myProject, myHandler, myTitle) {
        @Override public void onSuccess() {
          commonOnSuccess(LOCK, resultHandler);
          completed.set(true);
        }
        @Override public void onCancel() {
          commonOnCancel(LOCK, resultHandler);
          completed.set(true);
        }
      };
      if (myProgressIndicator == null) {
        GitVcs.runInBackground(task);
      } else {
        task.runAlone();
      }
    }

    if (sync) {
      while (!completed.get()) {
        try {
          synchronized (LOCK) {
            LOCK.wait(50);
          }
        } catch (InterruptedException e) {
          LOG.info(e);
        }
      }
    }
  }

  private void commonOnSuccess(final Object LOCK, final GitTaskResultHandler resultHandler) {
    GitTaskResult res = !myHandler.errors().isEmpty() ? GitTaskResult.GIT_ERROR : GitTaskResult.OK;
    resultHandler.run(res);
    synchronized (LOCK) {
      LOCK.notifyAll();
    }
  }

  private void commonOnCancel(final Object LOCK, final GitTaskResultHandler resultHandler) {
    resultHandler.run(GitTaskResult.CANCELLED);
    synchronized (LOCK) {
      LOCK.notifyAll();
    }
  }

  private void addListeners(final TaskExecution task, final ProgressIndicator indicator) {
    if (indicator != null) {
      indicator.setIndeterminate(myProgressAnalyzer == null);
    }
    // When receives an error line, adds a VcsException to the GitHandler.
    final GitLineHandlerListener listener = new GitLineHandlerListener() {
      @Override
      public void processTerminated(int exitCode) {
        if (exitCode != 0 && !myHandler.isIgnoredErrorCode(exitCode)) {
          if (myHandler.errors().isEmpty()) {
            myHandler.addError(new VcsException(myHandler.getLastOutput()));
          }
        }
      }

      @Override
      public void startFailed(Throwable exception) {
        myHandler.addError(new VcsException("Git start failed: " + exception.getMessage(), exception));
      }

      @Override
      public void onLineAvailable(String line, Key outputType) {
        if (GitHandlerUtil.isErrorLine(line.trim())) {
          myHandler.addError(new VcsException(line));
        } else if (!StringUtil.isEmptyOrSpaces(line)) {
          myHandler.addLastOutput(line);
        }
        if (indicator != null) {
          indicator.setText2(line);
        }
        if (myProgressAnalyzer != null && indicator != null) {
          final double fraction = myProgressAnalyzer.analyzeProgress(line);
          if (fraction >= 0) {
            indicator.setFraction(fraction);
          }
        }
      }
    };

    if (myHandler instanceof GitLineHandler) {
      ((GitLineHandler)myHandler).addLineListener(listener);
    } else {
      myHandler.addListener(listener);
    }

    // disposes the timer
    myHandler.addListener(new GitHandlerListener() {
      @Override
      public void processTerminated(int exitCode) {
        task.dispose();
      }

      @Override
      public void startFailed(Throwable exception) {
        task.dispose();
      }
    });
  }

  public void setProgressAnalyzer(GitProgressAnalyzer progressAnalyzer) {
    myProgressAnalyzer = progressAnalyzer;
  }

  public void setProgressIndicator(ProgressIndicator progressIndicator) {
    myProgressIndicator = progressIndicator;
  }

  /**
   * We're using this interface here to work with Task, because standard {@link Task#run(com.intellij.openapi.progress.ProgressIndicator)}
   * is busy with timers.
   */
  private interface TaskExecution {
    void execute(ProgressIndicator indicator);
    void dispose();
  }

  // To add to {@link com.intellij.openapi.progress.BackgroundTaskQueue} a task must be {@link Task.Backgroundable},
  // so we can't have a single class representing a task: we have BackgroundableTask and ModalTask.
  // To minimize code duplication we use GitTaskDelegate.

  private abstract class BackgroundableTask extends Task.Backgroundable implements TaskExecution {
    private final GitTaskDelegate myDelegate;

    public BackgroundableTask(@Nullable final Project project, @NotNull GitHandler handler, @NotNull final String processTitle) {
      super(project, processTitle, true);
      myDelegate = new GitTaskDelegate(myProject, handler, this);
    }

    @Override
    public final void run(@NotNull ProgressIndicator indicator) {
      myDelegate.run(indicator);
    }

    public final void runAlone() {
      if (ApplicationManager.getApplication().isDispatchThread()) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> justRun());
      } else {
        justRun();
      }
    }

    private void justRun() {
      String oldTitle = myProgressIndicator.getText();
      myProgressIndicator.setText(myTitle);
      myDelegate.run(myProgressIndicator);
      myProgressIndicator.setText(oldTitle);
      if (myProgressIndicator.isCanceled()) {
        onCancel();
      } else {
        onSuccess();
      }
    }

    @Override
    public void execute(ProgressIndicator indicator) {
      addListeners(this, indicator);
      GitHandlerUtil.runInCurrentThread(myHandler, indicator, false, myTitle);
    }

    @Override
    public void dispose() {
      Disposer.dispose(myDelegate);
    }
  }

  private abstract class ModalTask extends Task.Modal implements TaskExecution {
    private final GitTaskDelegate myDelegate;

    public ModalTask(@Nullable final Project project, @NotNull GitHandler handler, @NotNull final String processTitle) {
      super(project, processTitle, true);
      myDelegate = new GitTaskDelegate(myProject, handler, this);
    }

    @Override
    public final void run(@NotNull ProgressIndicator indicator) {
      myDelegate.run(indicator);
    }

    @Override
    public void execute(ProgressIndicator indicator) {
      addListeners(this, indicator);
      GitHandlerUtil.runInCurrentThread(myHandler, indicator, false, myTitle);
    }

    @Override
    public void dispose() {
      Disposer.dispose(myDelegate);
    }
  }

  /**
   * Does the work which is common for BackgroundableTask and ModalTask.
   * Actually - starts a timer which checks if current progress indicator is cancelled.
   * If yes, kills the GitHandler.
   */
  private static class GitTaskDelegate implements Disposable {
    private final GitHandler myHandler;
    private ProgressIndicator myIndicator;
    private final TaskExecution myTask;
    private ScheduledFuture<?> myTimer;
    private final Project myProject;

    public GitTaskDelegate(Project project, GitHandler handler, TaskExecution task) {
      myProject = project;
      myHandler = handler;
      myTask = task;
      Disposer.register(myProject, this);
    }

    public void run(ProgressIndicator indicator) {
      myIndicator = indicator;
      myTimer = JobScheduler.getScheduler().scheduleWithFixedDelay(
        ()-> {
          if (myIndicator != null && myIndicator.isCanceled()) {
            try {
              if (myHandler != null) {
                myHandler.destroyProcess();
              }
            }
            finally {
              Disposer.dispose(this);
            }
          }
      }, 0, 200, TimeUnit.MILLISECONDS);
      myTask.execute(indicator);
    }

    @Override
    public void dispose() {
      if (myTimer != null) {
        myTimer.cancel(false);
      }
    }
  }

}
