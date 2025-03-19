// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.content.impl

import com.intellij.concurrency.captureThreadContext
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.UIBundle
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.MessageView
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture

internal class MessageViewImpl(private val project: Project, private val scope: CoroutineScope) : MessageView {
  private val toolWindow: Deferred<ToolWindow> =
    if (EDT.isCurrentThreadEdt()) {
      CompletableDeferred(registerToolWindow(project))
    }
    else {
      scope.async(Dispatchers.EDT, start = CoroutineStart.LAZY) { registerToolWindowAsync(project) }
    }

  private val postponedRunnables = ArrayList<Runnable>()

  init {
    StartupManager.getInstance(project).runAfterOpened {
      scope.launch {
        toolWindow.await()

        withContext(Dispatchers.EDT) {
          var err : Throwable? = null
          for (postponedRunnable in postponedRunnables) {
            try {
              postponedRunnable.run()
            }
            catch (e: Throwable) {
              if (e !is ControlFlowException) {
                thisLogger().error(e)
              }
              if (err == null) {
                err = e
              }
            }
          }

          postponedRunnables.clear()
          postponedRunnables.trimToSize()

          if (err != null) {
            throw err
          }
        }
      }
    }
  }

  override val contentManager: ContentManager
    get() {
      if (!toolWindow.isCompleted) {
        throw IllegalStateException("access contentManager when it's not yet available")
      }
      return toolWindow.asCompletableFuture().get().contentManager
    }

  override fun runWhenInitialized(runnable: Runnable) {
    // no locking here and in init because postponedRunnable is accessed from EDT only
    // see @RequiresEdt on [com.intellij.ui.content.MessageView.runWhenInitialized]
    if (!toolWindow.isCompleted) {
      postponedRunnables.add(captureThreadContext(runnable))
    }
    else {
      runnable.run()
    }
  }

  override suspend fun awaitInitialized() {
    toolWindow.await()
  }

  companion object {
    private const val TW_ID = ToolWindowId.MESSAGES_WINDOW
    private val TW_ICON = AllIcons.Toolwindows.ToolWindowMessages
    private val TW_TITLE = UIBundle.messagePointer("tool.window.name.messages")

    private suspend fun registerToolWindowAsync(project: Project): ToolWindow {
      return project.serviceAsync<ToolWindowManager>().registerToolWindow(TW_ID) { icon = TW_ICON; stripeTitle = TW_TITLE }
    }

    private fun registerToolWindow(project: Project): ToolWindow {
      return ToolWindowManager.getInstance(project).registerToolWindow(TW_ID) { icon = TW_ICON; stripeTitle = TW_TITLE }
    }
  }
}
