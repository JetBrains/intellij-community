// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.intellij.concurrency.JobScheduler
import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.Consumer
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.storage.HeavyProcessLatch
import java.util.concurrent.Future
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

/**
 * Creates new HeavyAwareExecutor.
 *
 * @property project                target project
 * @property delayMs                delay in milliseconds to execute the task after a heavy activity is finished
 * @property longActivityDurationMs length of activity in milliseconds that cancels the task
 */
class HeavyAwareExecutor(private val project: Project,
                         private val delayMs: Int,
                         private val longActivityDurationMs: Int,
                         parent: Disposable) : Disposable {
  private val executorService = MoreExecutors.listeningDecorator(AppExecutorUtil.getAppExecutorService())

  init {
    Disposer.register(parent, this)
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
  fun executeOutOfHeavyOrPowerSave(task: Consumer<in ProgressIndicator?>,
                                   indicator: ProgressIndicator): Future<*> {
    val executingListener = ExecutingHeavyOrPowerSaveListener(project, delayMs, this) {
      val future = executorService.submit { ProgressManager.getInstance().runProcess({ task.consume(indicator) }, indicator) }
      val disposable = Disposer.newDisposable()
      future.addListener({ Disposer.dispose(disposable) }, MoreExecutors.directExecutor())
      CancellingOnHeavyOrPowerSaveListener(project, indicator, longActivityDurationMs, disposable)
      future
    }
    return Futures.transformAsync(executingListener.future, { input: ListenableFuture<*>? -> input }, MoreExecutors.directExecutor())
  }

  override fun dispose() = Unit

  companion object {
    /**
     * Starts a task out of heavy activity after a delay.
     *
     * @param command a task to start
     * @param delayMs delay in milliseconds to wait after a heavy activity is finished
     */
    @JvmStatic
    fun executeOutOfHeavyProcessLater(command: Runnable, delayMs: Int) {
      HeavyProcessLatch.INSTANCE.queueExecuteOutOfHeavyProcess {
        JobScheduler.getScheduler().schedule(
          {
            if (HeavyProcessLatch.INSTANCE.isRunning) {
              executeOutOfHeavyProcessLater(command, delayMs)
            }
            else {
              command.run()
            }
          }, delayMs.toLong(), TimeUnit.MILLISECONDS)
      }
    }
  }
}

private class CancellingOnHeavyOrPowerSaveListener(project: Project,
                                                   private val indicator: ProgressIndicator,
                                                   private val longActivityDurationMs: Int,
                                                   disposable: Disposable) : HeavyProcessLatch.HeavyProcessListener, PowerSaveMode.Listener {
  private var future: ScheduledFuture<*>? = null

  init {
    HeavyProcessLatch.INSTANCE.addListener(disposable, this)
    project.messageBus.connect(disposable).subscribe(PowerSaveMode.TOPIC, this)
    scheduleCancel() // in case some sneaky heavy process started before we managed to add a listener
    powerSaveStateChanged() // or if power save mode was suddenly turned on
  }

  override fun processStarted(op: HeavyProcessLatch.Operation) = scheduleCancel()
  override fun processFinished(op: HeavyProcessLatch.Operation) = doNotCancel()

  override fun powerSaveStateChanged() {
    if (PowerSaveMode.isEnabled() && indicator.isRunning) indicator.cancel()
  }

  @Synchronized
  private fun scheduleCancel() {
    if (HeavyProcessLatch.INSTANCE.isRunning && future == null) {
      future = JobScheduler.getScheduler().schedule(
        { if (HeavyProcessLatch.INSTANCE.isRunning && indicator.isRunning) indicator.cancel() },
        longActivityDurationMs.toLong(),
        TimeUnit.MILLISECONDS)
    }
  }

  @Synchronized
  private fun doNotCancel() {
    if (!HeavyProcessLatch.INSTANCE.isRunning && future != null) {
      val oldFuture = future
      future = null
      oldFuture?.cancel(true)
    }
  }
}

private class ExecutingHeavyOrPowerSaveListener(project: Project, private val delayMs: Int, parent: Disposable,
                                                _task: Supplier<ListenableFuture<*>>) : PowerSaveMode.Listener, Disposable {
  private val task = AtomicReference<Supplier<ListenableFuture<*>>?>(null)
  val future: SettableFuture<ListenableFuture<*>> = SettableFuture.create()

  init {
    task.set(_task)
    project.messageBus.connect(this).subscribe(PowerSaveMode.TOPIC, this)
    Disposer.register(parent, this)
    tryRun()
  }

  override fun powerSaveStateChanged() = tryRun()

  private fun tryRun() {
    if (!PowerSaveMode.isEnabled()) {
      HeavyProcessLatch.INSTANCE.queueExecuteOutOfHeavyProcess {
        JobScheduler.getScheduler().schedule(
          {
            if (!HeavyProcessLatch.INSTANCE.isRunning && !PowerSaveMode.isEnabled()) {
              Disposer.dispose(this)
              task.getAndSet(null)?.let { runTask(it) }
            }
            else {
              tryRun()
            }
          }, delayMs.toLong(), TimeUnit.MILLISECONDS)
      }
    }
  }

  private fun runTask(_task: Supplier<out ListenableFuture<*>>) {
    try {
      future.set(_task.get())
    }
    catch (t: Throwable) {
      future.setException(t)
    }
  }

  override fun dispose() = Unit
}