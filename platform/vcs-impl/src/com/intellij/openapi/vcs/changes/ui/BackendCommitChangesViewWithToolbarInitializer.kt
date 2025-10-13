// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.application.UI
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.ProblemListener
import com.intellij.util.asDisposable
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcsUtil.VcsUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Changes view has reduced features set on Thin Client. Therefore, it makes sense to initialize some listeners
 * only on the backend.
 */
internal class BackendCommitChangesViewWithToolbarInitializer : CommitChangesViewWithToolbarPanel.Initializer {
  override fun init(scope: CoroutineScope, panel: CommitChangesViewWithToolbarPanel) {
    val busConnection = panel.project.messageBus.connect(scope)

    busConnection.subscribe(ProblemListener.TOPIC, OnProblemsUpdate(scope, panel))
    busConnection.subscribe(RemoteRevisionsCache.REMOTE_VERSION_CHANGED, Runnable { panel.scheduleRefresh() })
    busConnection.subscribe(ChangeListListener.TOPIC, OnChangeListsUpdate(panel))

    ChangesViewDnDSupport.install(panel.project, panel.changesView, scope.asDisposable())
  }

  private class OnChangeListsUpdate(private val panel: CommitChangesViewWithToolbarPanel) : ChangeListAdapter() {
    override fun changeListsChanged() {
      panel.scheduleRefresh()
    }

    override fun unchangedFileStatusChanged() {
      panel.scheduleRefresh()
    }

    override fun changedFileStatusChanged() {
      panel.scheduleRefresh()
      val changeListManager = ChangeListManagerImpl.getInstanceImpl(panel.project)
      ChangesViewManager.getInstance(panel.project).updateProgressComponent(changeListManager.getAdditionalUpdateInfo())
    }
  }

  private class OnProblemsUpdate(private val scope: CoroutineScope, private val panel: CommitChangesViewWithToolbarPanel) : ProblemListener {
    override fun problemsAppeared(file: VirtualFile) {
      refreshChangesViewNodeAsync(scope, file, panel.changesView)
    }

    override fun problemsDisappeared(file: VirtualFile) {
      refreshChangesViewNodeAsync(scope, file, panel.changesView)
    }

    private fun refreshChangesViewNodeAsync(scope: CoroutineScope, file: VirtualFile, changesView: ChangesListView) {
      scope.launch(Dispatchers.UI) {
        findNodeForFile(file, changesView)?.let { node ->
          changesView.model.nodeChanged(node)
        }
      }
    }

    private fun findNodeForFile(file: VirtualFile, changesView: ChangesListView): ChangesBrowserNode<*>? {
      val filePath = VcsUtil.getFilePath(file)
      return TreeUtil.findNode(changesView.root as DefaultMutableTreeNode) {
        val nodeFilePath = VcsTreeModelData.mapUserObjectToFilePath(it.userObject)
        filePath == nodeFilePath
      } as? ChangesBrowserNode<*>
    }
  }
}