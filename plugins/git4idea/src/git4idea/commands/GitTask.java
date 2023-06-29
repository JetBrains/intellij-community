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
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import git4idea.GitDisposable;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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

  private final @NotNull Project myProject;
  private final @NotNull GitLineHandler myHandler;
  private final @NlsContexts.ProgressTitle String myTitle;
  private final @Nullable GitProgressAnalyzer myProgressAnalyzer;
  private final @NotNull ProgressIndicator myProgressIndicator;

  public GitTask(@NotNull Project project,
                 @NotNull GitLineHandler handler,
                 @NotNull @NlsContexts.ProgressTitle String title,
                 @NotNull ProgressIndicator progressIndicator,
                 @Nullable GitProgressAnalyzer progressAnalyzer) {
    myProject = project;
    myHandler = handler;
    myTitle = title;
    myProgressIndicator = progressIndicator;
    myProgressAnalyzer = progressAnalyzer;
  }

  /**
   * @param sync          Set to {@code true} to make the calling thread wait for the task execution.
   * @param resultHandler Handle the result.
   */
  public void executeInBackground(boolean sync, final GitTaskResultHandler resultHandler) {
    BackgroundableTask task = new BackgroundableTask(myHandler, myTitle, resultHandler);
    task.runAlone(sync);
  }

  private class BackgroundableTask implements Disposable {
    private final @NotNull GitLineHandler myHandler;
    private final @NotNull @NlsContexts.ProgressTitle String myTitle;
    private final @NotNull GitTaskResultHandler myResultHandler;

    private final CountDownLatch myCountDown = new CountDownLatch(0);
    private @Nullable ScheduledFuture<?> myTimer;

    BackgroundableTask(@NotNull GitLineHandler handler,
                       @NotNull @NlsContexts.ProgressTitle String processTitle,
                       @NotNull GitTaskResultHandler resultHandler) {
      myHandler = handler;
      myTitle = processTitle;
      myResultHandler = resultHandler;

      Disposer.register(GitDisposable.getInstance(myProject), this);
    }

    public final void runAlone(boolean sync) {
      if (ApplicationManager.getApplication().isDispatchThread()) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> justRun());
      }
      else {
        justRun();
      }

      if (sync) {
        try {
          myCountDown.await();
        }
        catch (InterruptedException e) {
          LOG.warn(e);
        }
      }
    }

    private void justRun() {
      String oldTitle = myProgressIndicator.getText();
      myProgressIndicator.setText(myTitle);

      myTimer = JobScheduler.getScheduler().scheduleWithFixedDelay(this::checkCancellation, 0, 200, TimeUnit.MILLISECONDS);

      myProgressIndicator.setIndeterminate(myProgressAnalyzer == null);
      myHandler.addLineListener(new MyGitLineListener());

      GitHandlerUtil.runInCurrentThread(myHandler, myProgressIndicator, false, myTitle);

      myProgressIndicator.setText(oldTitle);
      if (myProgressIndicator.isCanceled()) {
        myResultHandler.run(GitTaskResult.CANCELLED);
      }
      else {
        boolean hasErrors = !myHandler.errors().isEmpty();
        myResultHandler.run(hasErrors ? GitTaskResult.GIT_ERROR : GitTaskResult.OK);
      }
      myCountDown.countDown();
    }

    /**
     * Checks if current progress indicator is cancelled in timer.
     * If yes, kills the GitHandler.
     */
    private void checkCancellation() {
      if (myProgressIndicator.isCanceled()) {
        try {
          myHandler.destroyProcess();
        }
        finally {
          Disposer.dispose(this);
        }
      }
    }

    @Override
    public void dispose() {
      if (myTimer != null) {
        myTimer.cancel(false);
      }
    }

    /**
     * When receives an error line, adds a VcsException to the GitHandler.
     */
    private class MyGitLineListener implements GitLineHandlerListener {
      @Override
      public void processTerminated(int exitCode) {
        if (exitCode != 0) {
          if (myHandler.errors().isEmpty()) {
            myHandler.addError(new VcsException(myHandler.getLastOutput()));
          }
        }
        Disposer.dispose(BackgroundableTask.this);
      }

      @Override
      public void startFailed(@NotNull Throwable exception) {
        myHandler.addError(new VcsException(GitBundle.message("git.executable.unknown.error.message", exception.getMessage()), exception));
        Disposer.dispose(BackgroundableTask.this);
      }

      @Override
      public void onLineAvailable(String line, Key outputType) {
        if (GitHandlerUtil.isErrorLine(line.trim())) {
          myHandler.addError(new VcsException(line));
        }
        else if (!StringUtil.isEmptyOrSpaces(line)) {
          myHandler.addLastOutput(line);
        }
        myProgressIndicator.setText2(line);
        if (myProgressAnalyzer != null) {
          final double fraction = myProgressAnalyzer.analyzeProgress(line);
          if (fraction >= 0) {
            myProgressIndicator.setFraction(fraction);
          }
        }
      }
    }
  }
}
