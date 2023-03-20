// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.content.impl

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.UIBundle
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.MessageView

internal class MessageViewImpl(project: Project) : MessageView {
  private var toolWindow: ToolWindow? = null
  private val postponedRunnables = ArrayList<Runnable>()

  init {
    StartupManager.getInstance(project).runAfterOpened {
      // in a unit test mode this code maybe executed in EDT, so, don't use here invokeLater
      // also, MessageView service maybe called directly from EDT
      AppUIExecutor.onUiThread().expireWith(project).execute {
        toolWindow = ToolWindowManager.getInstance(project).registerToolWindow(ToolWindowId.MESSAGES_WINDOW) {
          icon = AllIcons.Toolwindows.ToolWindowMessages
          stripeTitle = UIBundle.messagePointer("tool.window.name.messages")
        }
        for (postponedRunnable in postponedRunnables) {
          postponedRunnable.run()
        }
        postponedRunnables.clear()
      }
    }
  }

  override fun getContentManager(): ContentManager {
    return toolWindow!!.contentManager
  }

  override fun runWhenInitialized(runnable: Runnable) {
    if (toolWindow == null) {
      postponedRunnables.add(runnable)
    }
    else {
      runnable.run()
    }
  }
}