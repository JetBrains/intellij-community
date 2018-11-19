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
package com.intellij.vcs.log.impl;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

public class HeavyAwareExecutor implements Disposable {
  @NotNull private final Project myProject;
  @NotNull private final ExecutingHeavyOrPowerSaveListener myListener;
  private final int myLongActivityDurationMs;

  /**
   * Creates new HeavyAwareExecutor.
   *
   * @param project                target project
   * @param delayMs                delay in milliseconds to execute the task after a heavy activity is finished
   * @param longActivityDurationMs length of activity in milliseconds that cancels the task
   */
  public HeavyAwareExecutor(@NotNull Project project, int delayMs, int longActivityDurationMs, @NotNull Disposable parent) {
    myProject = project;
    myLongActivityDurationMs = longActivityDurationMs;
    myListener = new ExecutingHeavyOrPowerSaveListener(project, delayMs, this);

    Disposer.register(parent, this);
  }

  /**
   * Starts a task in background after heavy process is finished and there is no power save mode after.
   * Task is not started until there some amount of time has passed since the last heavy activity.
   * When a "long" heavy activity is started during task execution, task is cancelled.
   * Task is also cancelled when user turns power save mode on.
   *
   * @param task      task to execute
   * @param indicator progress indicator for executing the task
   */
  public Future<?> executeOutOfHeavyOrPowerSave(@NotNull Consumer<ProgressIndicator> task, @NotNull String title,
                                                @NotNull ProgressIndicator indicator) {
    return Futures.transformAsync(
      myListener.addTask(() -> {
        Disposable disposable = Disposer.newDisposable();
        ListenableFuture<?> future = runAsync(myProject, task, title, indicator, () -> Disposer.dispose(disposable));

        new CancellingOnHeavyOrPowerSaveListener(myProject, indicator, myLongActivityDurationMs, disposable);
        return future;
      }), input -> input, directExecutor());
  }

  @Override
  public void dispose() {
  }

  @NotNull
  private static ListenableFuture<?> runAsync(@NotNull Project project,
                                              @NotNull Consumer<ProgressIndicator> task,
                                              @NotNull String title,
                                              @NotNull ProgressIndicator indicator,
                                              @NotNull Runnable continuation) {
    SettableFuture<?> future = SettableFuture.create();
    Task.Backgroundable wrapper = new Task.Backgroundable(project, title, true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          task.consume(indicator);
        }
        finally {
          future.set(null);
        }
      }
    };
    ((CoreProgressManager)ProgressManager.getInstance()).runProcessWithProgressAsynchronously(wrapper, indicator, continuation);
    return future;
  }

  /**
   * Starts a task out of heavy activity after a delay.
   *
   * @param command a task to start
   * @param delayMs delay in milliseconds to wait after a heavy activity is finished
   */
  public static void executeOutOfHeavyProcessLater(@NotNull Runnable command, int delayMs) {
    HeavyProcessLatch.INSTANCE.executeOutOfHeavyProcess(() -> JobScheduler.getScheduler().schedule(() -> {
      if (HeavyProcessLatch.INSTANCE.isRunning()) {
        executeOutOfHeavyProcessLater(command, delayMs);
      }
      else {
        command.run();
      }
    }, delayMs, TimeUnit.MILLISECONDS));
  }

  private static class CancellingOnHeavyOrPowerSaveListener implements HeavyProcessLatch.HeavyProcessListener, PowerSaveMode.Listener {
    @NotNull private final ProgressIndicator myIndicator;
    private final int myLongActivityDurationMs;

    @Nullable private ScheduledFuture<?> myFuture = null;

    CancellingOnHeavyOrPowerSaveListener(@NotNull Project project,
                                         @NotNull ProgressIndicator indicator,
                                         int logActivityDurationMs,
                                         @NotNull Disposable disposable) {
      myIndicator = indicator;
      myLongActivityDurationMs = logActivityDurationMs;

      HeavyProcessLatch.INSTANCE.addListener(this, disposable);
      project.getMessageBus().connect(disposable).subscribe(PowerSaveMode.TOPIC, this);

      scheduleCancel(); // in case some sneaky heavy process started before we managed to add a listener
      powerSaveStateChanged(); // or if power save mode was suddenly turned on
    }

    @Override
    public void processStarted() {
      scheduleCancel();
    }

    @Override
    public void processFinished() {
      doNotCancel();
    }

    @Override
    public void powerSaveStateChanged() {
      if (PowerSaveMode.isEnabled() && myIndicator.isRunning()) myIndicator.cancel();
    }

    private synchronized void scheduleCancel() {
      if (HeavyProcessLatch.INSTANCE.isRunning() && myFuture == null) {
        myFuture = JobScheduler.getScheduler().schedule(() -> {
          if (HeavyProcessLatch.INSTANCE.isRunning() && myIndicator.isRunning()) myIndicator.cancel();
        }, myLongActivityDurationMs, TimeUnit.MILLISECONDS);
      }
    }

    private synchronized void doNotCancel() {
      if (!HeavyProcessLatch.INSTANCE.isRunning() && myFuture != null) {
        ScheduledFuture<?> future = myFuture;
        myFuture = null;
        future.cancel(true);
      }
    }
  }

  private static class ExecutingHeavyOrPowerSaveListener implements PowerSaveMode.Listener {
    @NotNull private final AtomicReference<List<Runnable>> myTasksToRun = new AtomicReference<>(ContainerUtil.newArrayList());
    private final int myDelayMs;

    ExecutingHeavyOrPowerSaveListener(@NotNull Project project, int delayMs, @NotNull Disposable parent) {
      myDelayMs = delayMs;
      project.getMessageBus().connect(parent).subscribe(PowerSaveMode.TOPIC, this);
    }

    @NotNull
    public <T> ListenableFuture<ListenableFuture<T>> addTask(@NotNull Computable<ListenableFuture<T>> task) {
      SettableFuture<ListenableFuture<T>> future = SettableFuture.create();
      myTasksToRun.getAndUpdate(tasks -> ContainerUtil.concat(tasks, Collections.singletonList(wrap(task, future))));
      tryRun();
      return future;
    }

    @NotNull
    private static <T> Runnable wrap(@NotNull Computable<ListenableFuture<T>> task, @NotNull SettableFuture<ListenableFuture<T>> future) {
      return () -> {
        try {
          future.set(task.compute());
        }
        catch (Throwable t) {
          future.setException(t);
        }
      };
    }

    @Override
    public void powerSaveStateChanged() {
      tryRun();
    }

    private void tryRun() {
      if (!PowerSaveMode.isEnabled()) {
        HeavyProcessLatch.INSTANCE.executeOutOfHeavyProcess(() -> JobScheduler.getScheduler().schedule(() -> {
          if (!HeavyProcessLatch.INSTANCE.isRunning() && !PowerSaveMode.isEnabled()) {
            List<Runnable> tasks = myTasksToRun.getAndSet(ContainerUtil.newArrayList());
            tasks.forEach(Runnable::run);
          }
          else {
            tryRun();
          }
        }, myDelayMs, TimeUnit.MILLISECONDS));
      }
    }
  }
}
