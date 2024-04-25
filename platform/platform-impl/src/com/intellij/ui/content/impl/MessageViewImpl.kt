// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.content.impl

import com.intellij.concurrency.captureThreadContext
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.UIBundle
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.MessageView
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture

internal class MessageViewImpl(project: Project, scope: CoroutineScope) : MessageView {
  private val toolWindow = scope.async(Dispatchers.EDT, start = CoroutineStart.LAZY) {
    project.serviceAsync<ToolWindowManager>().registerToolWindow(ToolWindowId.MESSAGES_WINDOW) {
      icon = AllIcons.Toolwindows.ToolWindowMessages
      stripeTitle = UIBundle.messagePointer("tool.window.name.messages")
    }
  }
  private val postponedRunnables = ArrayList<Runnable>()

  init {
    StartupManager.getInstance(project).runAfterOpened {
      scope.launch {
        toolWindow.await()

        withContext(Dispatchers.EDT) {
          for (postponedRunnable in postponedRunnables) {
            postponedRunnable.run()
          }

          postponedRunnables.clear()
          postponedRunnables.trimToSize()
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
}
