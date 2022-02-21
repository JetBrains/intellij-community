// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowTabController
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHProjectRepositoriesManager

@Service
internal class GHPRToolWindowController(private val project: Project) : Disposable {
  @RequiresEdt
  fun isAvailable(): Boolean {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(GHPRToolWindowFactory.ID) ?: return false
    return toolWindow.isAvailable
  }

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

  internal class RepoListListener(private val project: Project) : GHProjectRepositoriesManager.ListChangesListener {

    override fun onRepositoryListChanges(newList: Set<GHGitRepositoryMapping>) {
      val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(GHPRToolWindowFactory.ID) ?: return
        toolWindow.isAvailable = newList.isNotEmpty()
    }
  }
}