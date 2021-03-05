// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowTabController
import org.jetbrains.plugins.github.util.GHProjectRepositoriesManager

@Service
internal class GHPRToolWindowController(private val project: Project) : Disposable {
  private val repositoryManager = project.service<GHProjectRepositoriesManager>()

  init {
    repositoryManager.addRepositoryListChangedListener(this) {
      ToolWindowManager.getInstance(project).getToolWindow(GHPRToolWindowFactory.ID)?.isAvailable = isAvailable()
    }
  }

  @RequiresEdt
  fun isAvailable(): Boolean = repositoryManager.knownRepositories.isNotEmpty()

  @RequiresEdt
  fun activate(onActivated: ((GHPRToolWindowTabController) -> Unit)? = null) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(GHPRToolWindowFactory.ID) ?: return
    toolWindow.activate {
      val controller = toolWindow.contentManager.selectedContent?.getUserData(GHPRToolWindowTabController.KEY)
      if (controller != null && onActivated != null) {
        onActivated(controller)
      }
    }
  }

  fun getTabController(): GHPRToolWindowTabController? = ToolWindowManager.getInstance(project)
    .getToolWindow(GHPRToolWindowFactory.ID)
    ?.let { it.contentManagerIfCreated?.selectedContent?.getUserData(GHPRToolWindowTabController.KEY) }

  override fun dispose() {
  }
}