/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.openapi.progress;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PairConsumer;
import com.intellij.util.PlusMinus;
import com.intellij.util.concurrency.QueueProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Runs backgroundable tasks one by one.
 * To add a task to the queue use {@link #run(com.intellij.openapi.progress.Task.Backgroundable)}
 * BackgroundTaskQueue may have a title - this title will be used if the task which is currently running doesn't have a title.
 * 
 * @author yole
 * @author Kirill Likhodedov
 */
@SomeQueue
public class BackgroundTaskQueue {
  private final static String ourMonitorFlag = "monitor.background.queue.load";
  private static final Logger LOG = Logger.getInstance(BackgroundTaskQueue.class.getName());
  //private final Project myProject;
  private final QueueProcessor<Pair<Task.Backgroundable, Getter<ProgressIndicator>>> myProcessor;
  private Boolean myForcedTestMode;
  private final PlusMinus<String> myMonitor;

  public BackgroundTaskQueue(@Nullable Project project, @NotNull String title) {
    this(project, title, null);
  }

  public BackgroundTaskQueue(@Nullable final Project project, @NotNull String title, final Boolean forcedHeadlessMode) {
    myMonitor = Boolean.TRUE.equals(Boolean.getBoolean(ourMonitorFlag)) ? new BackgroundTasksMonitor(title) : new PlusMinus.Empty<String>();
    final boolean headless = forcedHeadlessMode != null ? forcedHeadlessMode : ApplicationManager.getApplication().isHeadlessEnvironment();

    final QueueProcessor.ThreadToUse threadToUse = headless ? QueueProcessor.ThreadToUse.POOLED : QueueProcessor.ThreadToUse.AWT;
    final PairConsumer<Pair<Task.Backgroundable, Getter<ProgressIndicator>>, Runnable> consumer
      = headless ? new BackgroundableHeadlessRunner() : new BackgroundableUnderProgressRunner(title, project, myMonitor);

    myProcessor = new QueueProcessor<Pair<Task.Backgroundable, Getter<ProgressIndicator>>>(consumer, true,
                                                                                           threadToUse, new Condition<Object>() {
        @Override public boolean value(Object o) {
          if (project == null) return ApplicationManager.getApplication().isDisposed();
          if (project.isDefault()) {
            return project.isDisposed();
          } else {
            return !ApplicationManager.getApplication().isUnitTestMode() && !project.isOpen() || project.isDisposed();
          }
        }
      });
  }

  public void clear() {
    myProcessor.clear();
  }

  public boolean isEmpty() {
    return myProcessor.isEmpty();
  }
  
  public void waitForTasksToFinish() {
    myProcessor.waitFor();
  }

  public void run(Task.Backgroundable task) {
    run(task, null, null);
  }

  public void run(Task.Backgroundable task, final ModalityState state, final Getter<ProgressIndicator> pi) {
    myMonitor.plus(task.getTitle());
    if (isTestMode()) { // test tasks are executed in this thread without the progress manager
      RunBackgroundable.runIfBackgroundThread(task, new EmptyProgressIndicator(), null);
    } else {
      myProcessor.add(new Pair<Task.Backgroundable, Getter<ProgressIndicator>>(task, pi), state);
    }
  }

  private static class BackgroundableHeadlessRunner implements PairConsumer<Pair<Task.Backgroundable, Getter<ProgressIndicator>>, Runnable> {
    @Override
    public void consume(Pair<Task.Backgroundable, Getter<ProgressIndicator>> pair, Runnable runnable) {
      final Task.Backgroundable backgroundable = pair.getFirst();
      // synchronously
      ProgressManager.getInstance().run(backgroundable);
      runnable.run();
    }
  }

  private static class BackgroundableUnderProgressRunner implements PairConsumer<Pair<Task.Backgroundable, Getter<ProgressIndicator>>, Runnable> {
    private final String myTitle;
    private final Project myProject;
    private final PlusMinus<String> myMonitor;

    public BackgroundableUnderProgressRunner(String title, final Project project, PlusMinus<String> monitor) {
      myTitle = title;
      myProject = project;
      myMonitor = monitor;
    }

    @Override
    public void consume(final Pair<Task.Backgroundable, Getter<ProgressIndicator>> pair, final Runnable runnable) {
      myMonitor.minus(pair.getFirst().getTitle());
      final Task.Backgroundable backgroundable = pair.getFirst();
      final ProgressIndicator[] pi = new ProgressIndicator[1];
      final boolean taskTitleIsEmpty = StringUtil.isEmptyOrSpaces(backgroundable.getTitle());

      final Runnable wrappedTask = new Runnable() {
        @Override
        public void run() {
          // calls task's run and onCancel() or onSuccess(); call continuation after task.run()
          RunBackgroundable.runIfBackgroundThread(backgroundable,
            pi[0] == null ? ProgressManager.getInstance().getProgressIndicator() : pi[0], runnable);
        }
      };

      final ProgressManager pm = ProgressManager.getInstance();
      if (backgroundable.isConditionalModal() && ! backgroundable.shouldStartInBackground()) {
        pm.runProcessWithProgressSynchronously(wrappedTask, taskTitleIsEmpty ? myTitle : backgroundable.getTitle(),
                                               backgroundable.isCancellable(), myProject);
      } else {
        if (pair.getSecond() != null) {
          pi[0] = pair.getSecond().get();
        }
        if (pi[0] == null) {
          if (taskTitleIsEmpty) {
            backgroundable.setTitle(myTitle);
          }
          pi[0] = new BackgroundableProcessIndicator(backgroundable);
        }

        ProgressManagerImpl.runProcessWithProgressAsynchronously(backgroundable, pi[0], runnable);
      }
    }
  }

  public boolean isTestMode() {
    if (myForcedTestMode != null) return myForcedTestMode;
    return ApplicationManager.getApplication().isUnitTestMode();
  }

  @TestOnly
  public void setForcedTestMode(Boolean forcedTestMode, Disposable parentDisposable) {
    myForcedTestMode = forcedTestMode;
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        myForcedTestMode = null;
      }
    });
  }
}
