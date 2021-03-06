// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent
import com.intellij.ui.content.Content
import com.intellij.util.ui.update.UiNotifyConnector.doWhenFirstShown

open class CommitTabTitleUpdater(val tree: ChangesTree, val tabName: String, val defaultTitle: () -> String?) : Disposable {
  private val branchComponent = CurrentBranchComponent(tree).also { Disposer.register(this, it) }

  val project: Project get() = tree.project

  var pathsProvider: () -> Iterable<FilePath> by branchComponent::pathsProvider

  open fun start() {
    doWhenFirstShown(tree) { updateTab() } // as UI components could be created before tool window `Content`

    branchComponent.addChangeListener(this::updateTab, this)
    Disposer.register(this) { setDefaultTitle() }
  }

  open fun updateTab() {
    val tab = getTab() ?: return

    val branch = branchComponent.text
    tab.displayName = if (branch?.isNotBlank() == true) message("tab.title.commit.to.branch", branch) else message("tab.title.commit")
    tab.description = branchComponent.toolTipText
  }

  fun setDefaultTitle() {
    val tab = getTab() ?: return

    tab.displayName = defaultTitle()
    tab.description = null
  }

  override fun dispose() = Unit

  private fun getTab(): Content? =
    ChangesViewContentManager.getInstance(project).findContents { it.tabName == tabName }.firstOrNull()
}