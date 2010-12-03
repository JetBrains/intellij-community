/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.VcsException;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A Task to run the given GitHandler with ability to cancel it.
 * Cancellation is implemented with a {@link java.util.Timer} which checks whether the ProgressIndicator was cancelled and kills
 * the GitHandler in that case.
 *
 * A GitTask may be executed synchronously ({@link #execute()} or asynchronously ({@link #executeAsync(GitTask.ResultHandler)}.
 * Result of the execution is encapsulated in {@link GitTaskResult}.
 *
 * @see {@link git4idea.commands.GitHandler#kill()}
 * @author Kirill Likhodedov
 */
public class GitTask {

  private final Project myProject;
  private final GitHandler myHandler;
  private final String myTitle;
  private final AtomicReference<GitTaskResult> myResult = new AtomicReference<GitTaskResult>(GitTaskResult.INITIAL);

  public interface ResultHandler {
    void run(GitTaskResult result);
  }

  public GitTask(Project project, GitHandler handler, String title) {
    myProject = project;
    myHandler = handler;
    myTitle = title;
  }

  /**
   * Executes this task synchronously, with a modal progress dialog.
   * @return Result of the task execution.
   */
  public GitTaskResult execute() {
    ModalTask task = new ModalTask(myProject, myHandler, myTitle) {
      public void execute(ProgressIndicator indicator) {
        GitHandlerUtil.runInCurrentThread(myHandler, indicator, true, myTitle);
      }

      @Override
      public void onSuccess() {
        if (!myHandler.errors().isEmpty()) {
          myResult.set(GitTaskResult.GIT_ERROR);
        } else {
          myResult.set(GitTaskResult.OK);
        }
      }

      @Override
      public void onCancel() {
        myResult.set(GitTaskResult.CANCELLED);
      }
    };

    addListeners(task);
    ProgressManager.getInstance().run(task);
    return myResult.get();
  }

  /**
   * Executes this task asynchronously, in backgrond. Calls the resultHandler when finished.
   * @param resultHandler callback called after the task has finished or was cancelled by user or automatically.
   */
  public void executeAsync(final ResultHandler resultHandler) {
    BackgroundableTask task = new BackgroundableTask(myProject, myHandler, myTitle) {
      public void execute(ProgressIndicator indicator) {
        GitHandlerUtil.runInCurrentThread(myHandler, indicator, true, myTitle);
      }

      @Override
      public void onSuccess() {
        if (!myHandler.errors().isEmpty()) { // TODO: handle errors smarter: an error may be not a complete failure.
          myResult.set(GitTaskResult.GIT_ERROR);
        } else {
          myResult.set(GitTaskResult.OK);
        }
        resultHandler.run(myResult.get());
      }

      @Override
      public void onCancel() {
        myResult.set(GitTaskResult.CANCELLED);
        resultHandler.run(GitTaskResult.CANCELLED);
      }
    };

    addListeners(task);
    GitVcs.getInstance(myProject).runInBackground(task);
  }

  private void addListeners(final TaskExecution task) {
    // When receives an error line, adds a VcsException to the GitHandler.
    final GitLineHandlerListener errorListener = new GitLineHandlerListener() {
      @Override
      public void processTerminated(int exitCode) {
        if (exitCode != 0 && !myHandler.isIgnoredErrorCode(exitCode)) {
          if (myHandler.errors().isEmpty()) {
            myHandler.addError(new VcsException(GitBundle.message("git.error.exit", exitCode)));
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
        }
      }
    };

    myHandler.addListener(errorListener);
    if (myHandler instanceof GitLineHandler) {
      ((GitLineHandler)myHandler).addLineListener(errorListener);
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
    private GitTaskDelegate myDelegate;

    public BackgroundableTask(@Nullable final Project project, @NotNull GitHandler handler, @NotNull final String processTitle) {
      super(project, processTitle, true);
      myDelegate = new GitTaskDelegate(myProject, handler, this);
    }

    @Override
    public final void run(@NotNull ProgressIndicator indicator) {
      myDelegate.run(indicator);
    }

    @Override
    public void dispose() {
      Disposer.dispose(myDelegate);
    }
  }

  private abstract class ModalTask extends Task.Modal implements TaskExecution {
    private GitTaskDelegate myDelegate;

    public ModalTask(@Nullable final Project project, @NotNull GitHandler handler, @NotNull final String processTitle) {
      super(project, processTitle, true);
      myDelegate = new GitTaskDelegate(myProject, handler, this);
    }

    @Override
    public final void run(@NotNull ProgressIndicator indicator) {
      myDelegate.run(indicator);
    }

    @Override
    public void dispose() {
      Disposer.dispose(myDelegate);
    }
  }

  /**
   * Does the work which is common for BackgrounableTask and ModalTask.
   * Actually - starts a timer which checks if current progress indicator is cancelled.
   * If yes, kills the GitHandler.
   */
  private static class GitTaskDelegate implements Disposable {
    private GitHandler myHandler;
    private ProgressIndicator myIndicator;
    private TaskExecution myTask;
    private Timer myTimer;
    private Project myProject;

    public GitTaskDelegate(Project project, GitHandler handler, TaskExecution task) {
      myProject = project;
      myHandler = handler;
      myTask = task;
      Disposer.register(myProject, this);
    }

    public void run(ProgressIndicator indicator) {
      myIndicator = indicator;
      myTimer = new Timer();
      myTimer.schedule(new TimerTask() {
        @Override
        public void run() {
          if (myIndicator != null && myIndicator.isCanceled()) {
            if (myHandler != null) {
              myHandler.destroyProcess();
            }
            Disposer.dispose(GitTaskDelegate.this);
          }
        }
      }, 0, 200);
      myTask.execute(indicator);
    }

    public void dispose() {
      myTimer.cancel();
    }
  }

}
