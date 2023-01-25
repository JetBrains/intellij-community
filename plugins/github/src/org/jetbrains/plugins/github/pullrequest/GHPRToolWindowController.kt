// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.ClientProperty
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowContentController
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowLoginController
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowRepositoryContentController
import java.util.concurrent.CompletableFuture

@Service
internal class GHPRToolWindowController(private val project: Project) {
  @RequiresEdt
  fun isAvailable(): Boolean {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(GHPRToolWindowFactory.ID) ?: return false
    return toolWindow.isAvailable
  }

  @RequiresEdt
  fun activate(): CompletableFuture<GHPRToolWindowContentController> {
    val result = CompletableFuture<GHPRToolWindowContentController>()

    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(GHPRToolWindowFactory.ID)
                     ?: run {
                       result.cancel(true)
                       return result
                     }

    toolWindow.activate {
      val controller = ClientProperty.get(toolWindow.component, GHPRToolWindowContentController.KEY)
      if (controller != null) {
        result.complete(controller)
      }
      else {
        result.cancel(true)
      }
    }
    return result
  }

  fun getContentController(): GHPRToolWindowContentController? =
    ToolWindowManager.getInstance(project)
    .getToolWindow(GHPRToolWindowFactory.ID)?.component
    ?.let { ClientProperty.get(it, GHPRToolWindowContentController.KEY) }
}