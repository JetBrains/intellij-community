// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.codeWithMe.ClientId
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Async
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.function.Consumer

/**
 * Please, don't use this service. Use [DumbService.runWhenSmart] to schedule runnables in smart mode.
 */
@Experimental
@Internal
@Service(Service.Level.PROJECT)
class SmartModeScheduler(private val project: Project) : Disposable {
  private class RunnableDelegate(val task: Runnable, private val executor: Consumer<in Runnable>) : Runnable {
    override fun run() {
      executor.accept(task)
    }
  }

  private val myRunWhenSmartQueue: Deque<Runnable> = ConcurrentLinkedDeque()

  init {
    project.messageBus.simpleConnect().subscribe<DynamicPluginListener>(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
      override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        myRunWhenSmartQueue.removeIf { runnable ->
          val unwrappedRunnable = if (runnable is RunnableDelegate) runnable.task else runnable
          val classLoader: ClassLoader = unwrappedRunnable.javaClass.classLoader
          classLoader is PluginAwareClassLoader && (classLoader as PluginAwareClassLoader).pluginId == pluginDescriptor.pluginId
        }
      }
    })
  }

  fun addLast(runnable: Runnable) {
    val executor = ClientId.decorateRunnable(runnable)
    myRunWhenSmartQueue.addLast(if (executor === runnable) runnable else RunnableDelegate(runnable) { executor.run() })
  }

  fun runAllWhileSmartInThisThread() {
    // It may happen that one of the pending runWhenSmart actions triggers new dumb mode;
    // in this case we should quit processing pending actions and postpone them until the newly started dumb mode finishes.
    while (!DumbService.isDumb(project)) {
      val runnable = myRunWhenSmartQueue.pollFirst() ?: break
      doRun(runnable)
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

  fun clear() {
    myRunWhenSmartQueue.clear()
  }

  override fun dispose() {
    clear()
  }

  companion object {
    val LOG: Logger = logger<SmartModeScheduler>()
  }
}