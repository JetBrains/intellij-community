// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.content.Content
import com.intellij.util.ui.update.UiNotifyConnector
import com.intellij.vcs.branch.BranchData
import com.intellij.vcs.branch.BranchPresentation
import com.intellij.vcs.commit.CommitTabTitleUpdater
import com.intellij.vcs.log.runInEdt
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import java.beans.PropertyChangeEvent

abstract class SimpleTabTitleUpdater(private val tree: ChangesTree, private val tabName: String) : Disposable {
  private val disposableFlag = Disposer.newCheckedDisposable()
  private var branches = emptySet<BranchData>()
    set(value) {
      if (field == value) return
      field = value
      updatePresentation()
    }
  private val groupingListener: (evt: PropertyChangeEvent) -> Unit = { refresh() }

  init {
    UiNotifyConnector.doWhenFirstShown(tree, Runnable { refresh() })
    tree.addGroupingChangeListener(groupingListener)
    val busConnection = tree.project.messageBus.connect(this)
    busConnection.subscribe(GitRepository.GIT_REPO_CHANGE,
                            GitRepositoryChangeListener { runInEdt(disposableFlag) { refresh() } })
    busConnection.subscribe(ChangesViewContentManagerListener.TOPIC, object : ChangesViewContentManagerListener {
      override fun toolWindowMappingChanged() {
        updatePresentation() // listen for new content tabs
      }
    })
    Disposer.register(this, disposableFlag)
  }

  abstract fun getRoots(): Collection<VirtualFile>

  fun refresh() {
    branches = getBranches()
  }

  private fun updatePresentation() {
    if (disposableFlag.isDisposed) return
    val tab = getTab() ?: return

    tab.displayName = CommitTabTitleUpdater.getDisplayTabName(tree.project, tabName, BranchPresentation.getText(branches))
    tab.description = BranchPresentation.getTooltip(branches)
  }

  private fun getBranches(): Set<BranchData> {
    if (!shouldShowBranches()) {
      return emptySet()
    }
    return getRoots().mapNotNull { CurrentBranchComponent.getCurrentBranch(tree.project, it) }.toSet()
  }

  protected open fun shouldShowBranches(): Boolean {
    val groupingSupport = tree.groupingSupport
    return !groupingSupport.isAvailable(ChangesGroupingSupport.REPOSITORY_GROUPING) ||
           !groupingSupport[ChangesGroupingSupport.REPOSITORY_GROUPING]
  }

  private fun getTab(): Content? {
    return ChangesViewContentManager.getInstance(tree.project).findContent(tabName)
  }

  override fun dispose() {
    tree.removeGroupingChangeListener(groupingListener)
    branches = emptySet()
  }
}