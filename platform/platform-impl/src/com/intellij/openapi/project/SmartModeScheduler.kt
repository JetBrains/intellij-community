// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.codeWithMe.ClientId
import com.intellij.concurrency.captureThreadContext
import com.intellij.concurrency.resetThreadContext
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.childScope
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Async
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/**
 * Please, don't use this service. Use [DumbService.runWhenSmart] to schedule runnables in smart mode.
 *
 * Scheduled runnables will be executed on EDT thread after all three conditions met:
 * 1. There is no scanning in progress, and initial project scanning (on project open) has finished
 * 2. There is no dumb mode
 * 3. All the [com.intellij.openapi.startup.StartupActivity] completed
 *
 * It is important to note that while dumb mode cannot start without write action, scanning can start without write action, so by the
 * moment when runnable executes scanning can run, but it is guaranteed that initial scanning on project open has finished.
 */
@Internal
@Service(Service.Level.PROJECT)
class SmartModeScheduler(private val project: Project, sc: CoroutineScope) : Disposable {
  private class RunnableDelegate(val task: Runnable, private val executor: Consumer<in Runnable>) : Runnable {
    override fun run() {
      executor.accept(task)
    }
  }

  private val myRunWhenSmartQueue: Deque<Runnable> = ConcurrentLinkedDeque()

  private val projectOpening: AtomicBoolean = AtomicBoolean(true)

  private val dumbServiceImpl = project.service<DumbService>() as DumbServiceImpl
  private val filesScannerExecutor = project.service<UnindexedFilesScannerExecutor>()
  private val projectDumbState: StateFlow<DumbServiceImpl.DumbState> = dumbServiceImpl.dumbStateAsFlow
  private val projectScanningChanged: Flow<*> = filesScannerExecutor.startedOrStoppedEvent

  init {
    project.messageBus.simpleConnect().subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
      override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        myRunWhenSmartQueue.removeIf { runnable ->
          val unwrappedRunnable = if (runnable is RunnableDelegate) runnable.task else runnable
          val classLoader: ClassLoader = unwrappedRunnable.javaClass.classLoader
          classLoader is PluginAwareClassLoader && (classLoader as PluginAwareClassLoader).pluginId == pluginDescriptor.pluginId
        }
      }
    })

    sc.launch {
      projectScanningChanged.collect {
        onStateChanged()
      }
    }

    sc.childScope()
    sc.launch {
      projectDumbState.collect {
        onStateChanged()
      }
    }
  }

  private fun addLast(runnable: Runnable) {
    val executor = captureThreadContext(ClientId.decorateRunnable(runnable))
    myRunWhenSmartQueue.addLast(if (executor === runnable) runnable else RunnableDelegate(runnable) { executor.run() })
  }

  internal fun onProjectOpened() {
    LOG.info("Post-startup activity executed. Current mode: ${getCurrentMode()}")
    projectOpening.set(false)
    onStateChanged()
  }

  private fun onStateChanged() {
    if (isSmart()) {
      // Always reschedule execution to avoid unexpected write lock acquired.
      //
      // Note2: DumbService tracks modality by itself: exit event occurs in the same modality as the enter event.
      //        Use default modality here to avoid deadlocks like in WEB-59844 (dumb mode may start and end in non NON_MODAL contexts)
      ApplicationManager.getApplication().invokeLater(this::runAllWhileSmart, ModalityState.defaultModalityState(), project.disposed)
    }
  }

  fun runWhenSmart(runnable: Runnable) {
    if (isSmart() && ApplicationManager.getApplication().isDispatchThread) {
      // Execute immediately only because some tests expect this behavior. No production need.
      runnable.run()
    }
    else {
      addLast(runnable)
      onStateChanged()
    }
  }

  private fun runAllWhileSmart() {
    // We need EDT or WriteLock to make sure that dumb mode does not start while the method is in progress (see DumbServiceImpl.updateFinished).
    // Note that neither write lock nor EDT are enough to protect against switching to "almost smart": scanning can start at any moment 
    //   (it does not need write lock nor EDT), so the code should be ready for scanning to start at any moment.
    ThreadingAssertions.assertEventDispatchThread()

    // It may happen that one of the pending runWhenSmart actions triggers new dumb mode;
    // in this case we should quit processing pending actions and postpone them until the newly started dumb mode finishes.
    while (isSmart()) {
      val runnable = myRunWhenSmartQueue.pollFirst() ?: break
      resetThreadContext().use {
        doRun(runnable)
      }
    }
  }

  // Extracted to have a capture point
  private fun doRun(@Async.Execute runnable: Runnable) {
    try {
      runnable.run()
    }
    catch (e: ProcessCanceledException) {
      LOG.error("Task canceled: $runnable", Attachment("pce", e))
    }
    catch (e: Throwable) {
      LOG.error("Error executing task $runnable", e)
    }
  }

  private fun isSmart() = (getCurrentMode() == 0)
  fun getCurrentMode(): Int =
    (if (filesScannerExecutor.isRunning.value) SCANNING else 0) +
    (if (projectDumbState.value.isDumb) DUMB else 0) +
    (if (projectOpening.get()) OPENING else 0)

  fun clear() {
    myRunWhenSmartQueue.clear()
  }

  override fun dispose() {
    clear()
  }

  class SmartModeSchedulerStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
      project.service<SmartModeScheduler>().onProjectOpened()
    }
  }

  companion object {
    val LOG: Logger = logger<SmartModeScheduler>()
    const val SCANNING: Int = 1
    const val DUMB: Int = 1.shl(1)
    const val OPENING: Int = 1.shl(2)
  }
}