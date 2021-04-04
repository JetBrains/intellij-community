// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.google.common.util.concurrent.*;
import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class HeavyAwareExecutor implements Disposable {
  @NotNull private final Project myProject;
  @NotNull private final ListeningExecutorService myExecutorService;
  private final int myDelayMs;
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
    myDelayMs = delayMs;
    myLongActivityDurationMs = longActivityDurationMs;
    myExecutorService = MoreExecutors.listeningDecorator(AppExecutorUtil.getAppExecutorService());

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
  public Future<?> executeOutOfHeavyOrPowerSave(@NotNull Consumer<? super ProgressIndicator> task,
                                                @NotNull ProgressIndicator indicator) {
    ExecutingHeavyOrPowerSaveListener executingListener = new ExecutingHeavyOrPowerSaveListener(myProject, myDelayMs, this, () -> {
      ListenableFuture<?> future = myExecutorService.submit(() ->
          ProgressManager.getInstance().runProcess(() -> task.consume(indicator), indicator));

      Disposable disposable = Disposer.newDisposable();
      future.addListener(() -> Disposer.dispose(disposable), MoreExecutors.directExecutor());

      new CancellingOnHeavyOrPowerSaveListener(myProject, indicator, myLongActivityDurationMs, disposable);
      return future;
    });
    return Futures.transformAsync(executingListener.getFuture(), input -> input, MoreExecutors.directExecutor());
  }

  @Override
  public void dispose() {
  }

  /**
   * Starts a task out of heavy activity after a delay.
   *
   * @param command a task to start
   * @param delayMs delay in milliseconds to wait after a heavy activity is finished
   */
  public static void executeOutOfHeavyProcessLater(@NotNull Runnable command, int delayMs) {
    HeavyProcessLatch.INSTANCE.queueExecuteOutOfHeavyProcess(() -> JobScheduler.getScheduler().schedule(() -> {
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

    @Nullable private ScheduledFuture<?> myFuture;

    CancellingOnHeavyOrPowerSaveListener(@NotNull Project project,
                                         @NotNull ProgressIndicator indicator,
                                         int logActivityDurationMs,
                                         @NotNull Disposable disposable) {
      myIndicator = indicator;
      myLongActivityDurationMs = logActivityDurationMs;

      HeavyProcessLatch.INSTANCE.addListener(disposable, this);
      project.getMessageBus().connect(disposable).subscribe(PowerSaveMode.TOPIC, this);

      scheduleCancel(); // in case some sneaky heavy process started before we managed to add a listener
      powerSaveStateChanged(); // or if power save mode was suddenly turned on
    }

    @Override
    public void processStarted(@NotNull HeavyProcessLatch.Operation op) {
      scheduleCancel();
    }

    @Override
    public void processFinished(@NotNull HeavyProcessLatch.Operation op) {
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

  private static class ExecutingHeavyOrPowerSaveListener implements PowerSaveMode.Listener, Disposable {
    @NotNull private final AtomicReference<Supplier<ListenableFuture<?>>> myTask = new AtomicReference<>(null);
    private final int myDelayMs;
    private final SettableFuture<ListenableFuture<?>> myFuture;

    ExecutingHeavyOrPowerSaveListener(@NotNull Project project, int delayMs, @NotNull Disposable parent,
                                      @NotNull Supplier<ListenableFuture<?>> task) {
      myDelayMs = delayMs;
      myFuture = SettableFuture.create();
      myTask.set(task);

      project.getMessageBus().connect(this).subscribe(PowerSaveMode.TOPIC, this);
      Disposer.register(parent, this);

      tryRun();
    }

    @NotNull
    public ListenableFuture<ListenableFuture<?>> getFuture() {
      return myFuture;
    }

    @Override
    public void powerSaveStateChanged() {
      tryRun();
    }

    private void tryRun() {
      if (!PowerSaveMode.isEnabled()) {
        HeavyProcessLatch.INSTANCE.queueExecuteOutOfHeavyProcess(() -> JobScheduler.getScheduler().schedule(() -> {
          if (!HeavyProcessLatch.INSTANCE.isRunning() && !PowerSaveMode.isEnabled()) {
            Disposer.dispose(this);

            Supplier<ListenableFuture<?>> task = myTask.getAndSet(null);
            if (task != null) {
              runTask(task);
            }
          }
          else {
            tryRun();
          }
        }, myDelayMs, TimeUnit.MILLISECONDS));
      }
    }

    private void runTask(@NotNull Supplier<? extends ListenableFuture<?>> task) {
      try {
        myFuture.set(task.get());
      }
      catch (Throwable t) {
        myFuture.setException(t);
      }
    }

    @Override
    public void dispose() {
    }
  }
}
