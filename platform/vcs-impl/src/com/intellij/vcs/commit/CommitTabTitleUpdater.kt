// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.content.Content
import com.intellij.util.ui.update.UiNotifyConnector.Companion.doWhenFirstShown
import org.jetbrains.annotations.Nls

open class CommitTabTitleUpdater(val tree: ChangesTree,
                                 val tabName: String,
                                 val defaultTitle: () -> @Nls String?,
                                 pathsProvider: () -> Iterable<FilePath>) : Disposable {
  private val branchComponent = CurrentBranchComponent(tree, pathsProvider).also {
    Disposer.register(this, it)
  }

  val project: Project get() = tree.project

  open fun start() {
    // as UI components could be created before tool window `Content`
    doWhenFirstShown(component = tree, runnable = { updateTab() }, parent = this)

    branchComponent.addChangeListener(this::updateTab, this)
    Disposer.register(this) { setDefaultTitle() }
  }

  open fun updateTab() {
    val tab = getTab() ?: return

    tab.displayName = getDisplayTabName(project, tabName, branchComponent.text)
    tab.description = branchComponent.toolTipText
  }

  private fun setDefaultTitle() {
    val tab = getTab() ?: return

    tab.displayName = defaultTitle()
    tab.description = null
  }

  override fun dispose() {
  }

  private fun getTab(): Content? = ChangesViewContentManager.getInstance(project).findContent(tabName)

  companion object {
    fun getDisplayTabName(project: Project, tabName: String, branch: String?): @NlsContexts.TabTitle String? {
      if (ExperimentalUI.isNewUI()) {
        val contentsCount = ChangesViewContentManager.getToolWindowFor(project, tabName)?.contentManager?.contentCount ?: 0
        if (contentsCount == 1) return null else return message("tab.title.commit")
      }

      if (branch?.isNotBlank() == true) return message("tab.title.commit.to.branch", branch)

      return message("tab.title.commit")
    }
  }
}