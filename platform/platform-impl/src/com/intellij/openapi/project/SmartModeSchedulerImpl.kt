// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.concurrency.captureThreadContext
import com.intellij.concurrency.resetThreadContext
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Async
import java.util.Deque
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.function.Consumer

/**
 * Holds a scheduled runnable until the first [SmartModeProvider] reports smart mode for the project.
 */
internal class SmartModeSchedulerImpl(private val project: Project, scope: CoroutineScope) : SmartModeScheduler, Disposable {
  private class RunnableDelegate(val task: Runnable, private val executor: Consumer<in Runnable>) : Runnable {
    override fun run() {
      executor.accept(task)
    }
  }

  private val myRunWhenSmartQueue: Deque<Runnable> = ConcurrentLinkedDeque()

  private val providersChanged = MutableStateFlow(0)

  init {
    project.messageBus.simpleConnect().subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
      override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        removeQueued { classLoader -> classLoader.pluginId == pluginDescriptor.pluginId }
      }

      override fun pluginsUnloaded() {
        // A content module of the core plugin keeps the plugin id, so the purge by plugin id misses it.
        removeQueued { classLoader -> classLoader.state == PluginAwareClassLoader.UNLOAD_IN_PROGRESS }
      }
    })

    // The listener runs in a write action, so it only signals the change.
    SmartModeProvider.EP_NAME.addChangeListener(scope) {
      providersChanged.update { it + 1 }
    }

    scope.launch {
      // collectLatest drops the flow of the previous provider, so the class loader of an unloaded module is free.
      providersChanged.collectLatest {
        onStateChanged()
        SmartModeProvider.EP_NAME.extensionList.firstOrNull()?.modeChanged(project)?.collect {
          onStateChanged()
        }
      }
    }
  }

  private fun removeQueued(predicate: (PluginAwareClassLoader) -> Boolean) {
    myRunWhenSmartQueue.removeIf { runnable ->
      val unwrappedRunnable = if (runnable is RunnableDelegate) runnable.task else runnable
      val classLoader = unwrappedRunnable.javaClass.classLoader
      classLoader is PluginAwareClassLoader && predicate(classLoader)
    }
  }

  private fun addLast(runnable: Runnable) {
    val executor = captureThreadContext(runnable)
    myRunWhenSmartQueue.addLast(if (executor === runnable) runnable else RunnableDelegate(runnable) { executor.run() })
  }

  private fun onStateChanged() {
    if (canRunSmart()) {
      // Always reschedule execution to avoid unexpected write lock acquired.
      //
      // Note2: DumbService tracks modality by itself: exit event occurs in the same modality as the enter event.
      //        Use default modality here to avoid deadlocks like in WEB-59844 (dumb mode may start and end in non NON_MODAL contexts)
      ApplicationManager.getApplication().invokeLater(this::runAllWhileSmart, ModalityState.defaultModalityState(), project.disposed)
    }
  }

  override fun canRunSmart(): Boolean = getCurrentMode() == 0

  override fun runWhenSmart(runnable: Runnable) {
    if (canRunSmart() && ApplicationManager.getApplication().isDispatchThread) {
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
    while (canRunSmart()) {
      val runnable = myRunWhenSmartQueue.pollFirst() ?: break
      resetThreadContext {
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

  override fun getCurrentMode(): Int {
    // The default project is never dumb.
    if (project.isDefault) return 0
    return SmartModeProvider.EP_NAME.extensionList.firstOrNull()?.getCurrentMode(project) ?: 0
  }

  override fun dispose() {
    myRunWhenSmartQueue.clear()
  }

  private companion object {
    val LOG = logger<SmartModeSchedulerImpl>()
  }
}
