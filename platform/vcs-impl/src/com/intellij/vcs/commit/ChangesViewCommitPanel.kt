// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.contextModality
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.ChangesViewManager
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.LOCAL_CHANGES
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.getToolWindowFor
import com.intellij.openapi.wm.ToolWindow
import com.intellij.vcsUtil.VcsUtil.getFilePath
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.await
import kotlin.coroutines.coroutineContext

class ChangesViewCommitPanel @ApiStatus.Internal constructor(
  project: Project,
  private val changesView: ChangesListView,
) : ChangeListViewCommitPanel(project, changesView) {
  private var isHideToolWindowOnCommit = false

  init {
    ChangesViewCommitTabTitleUpdater(changesView, this, this).start()
  }

  override val isActive: Boolean get() = component.isVisible

  override fun activate(): Boolean {
    val toolWindow = getVcsToolWindow() ?: return false
    val contentManager = ChangesViewContentManager.getInstance(project)

    saveToolWindowState()
    changesView.isShowCheckboxes = true
    component.isVisible = true
    commitActionsPanel.isActive = true

    toolbar.updateActionsImmediately()

    contentManager.selectContent(LOCAL_CHANGES)
    toolWindow.activate({ commitMessage.requestFocusInMessage() }, false)
    return true
  }

  override fun deactivate(isOnCommit: Boolean) {
    if (isOnCommit && isHideToolWindowOnCommit) {
      getVcsToolWindow()?.hide(null)
    }

    clearToolWindowState()
    changesView.isShowCheckboxes = false
    component.isVisible = false
    commitActionsPanel.isActive = false

    toolbar.updateActionsImmediately()
  }

  private fun saveToolWindowState() {
    if (!isActive) {
      isHideToolWindowOnCommit = getVcsToolWindow()?.isVisible != true
    }
  }

  private fun clearToolWindowState() {
    isHideToolWindowOnCommit = false
  }

  private fun getVcsToolWindow(): ToolWindow? = getToolWindowFor(project, LOCAL_CHANGES)

  override suspend fun refreshChangesViewBeforeCommit() {
    val modalityState = coroutineContext.contextModality() ?: ModalityState.nonModal()
    ChangesViewManager.getInstanceEx(project).promiseRefresh(modalityState).await()
  }
}

private class ChangesViewCommitTabTitleUpdater(tree: ChangesTree, workflowUi: CommitWorkflowUi, disposable: Disposable)
  : CommitTabTitleUpdater(tree, LOCAL_CHANGES, { message("local.changes.tab") },
                          pathsProvider = {
                            val singleRoot = ProjectLevelVcsManager.getInstance(tree.project).allVersionedRoots.singleOrNull()
                            if (singleRoot != null) listOf(getFilePath(singleRoot)) else workflowUi.getDisplayedPaths()
                          }),
    ChangesViewContentManagerListener {
  init {
    Disposer.register(disposable, this)
  }

  override fun start() {
    super.start()
    project.messageBus.connect(this).subscribe(ChangesViewContentManagerListener.TOPIC, this)
  }

  override fun toolWindowMappingChanged() = updateTab()

  override fun updateTab() {
    if (!project.isCommitToolWindowShown) return
    super.updateTab()
  }
}
