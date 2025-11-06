// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.changes.viewModel

import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vcs.changes.ui.ChangesViewDnDSupport
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.vcs.impl.shared.changes.ChangesViewSettings
import com.intellij.problems.ProblemListener
import com.intellij.util.asDisposable
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcsUtil.VcsUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.swing.tree.DefaultMutableTreeNode

internal class BackendCommitChangesViewWithToolbarPanel(changesView: ChangesListView, cs: CoroutineScope) : CommitChangesViewWithToolbarPanel(changesView, cs) {
  override fun initPanel() {
    val busConnection = project.messageBus.connect(cs)
    busConnection.subscribe(ProblemListener.TOPIC, OnProblemsUpdate(cs, this))
    busConnection.subscribe(RemoteRevisionsCache.REMOTE_VERSION_CHANGED, Runnable { scheduleRefresh() })
    busConnection.subscribe(ChangeListListener.TOPIC, OnChangeListsUpdate(this))

    ChangesViewDnDSupport.install(project, changesView, cs.asDisposable())

    super.initPanel()
  }

  override fun getModelData(): ModelData {
    val changeListManager = ChangeListManagerImpl.getInstanceImpl(project)
    val changeLists = changeListManager.changeLists
    val unversionedFiles = changeListManager.unversionedFilesPaths

    val ignoredFiles = if (ChangesViewSettings.getInstance(project).showIgnored) changeListManager.ignoredFilePaths else emptyList()
    return ModelData(
      changeLists,
      unversionedFiles,
      ignoredFiles,
    ) { ChangesViewWorkflowManager.getInstance(project).commitWorkflowHandler?.isActive == true }
  }

  override fun synchronizeInclusion(changeLists: List<LocalChangeList>, unversionedFiles: List<FilePath>) {
    ChangesViewWorkflowManager.getInstance(project).commitWorkflowHandler?.synchronizeInclusion(changeLists, unversionedFiles)
  }
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
    scope.launch(Dispatchers.UiWithModelAccess) {
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