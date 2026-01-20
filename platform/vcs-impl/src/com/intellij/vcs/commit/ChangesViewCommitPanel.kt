// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.InclusionModel
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.ui.ChangeListChangesSupplier
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.LOCAL_CHANGES
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.getToolWindowFor
import com.intellij.openapi.vcs.changes.ui.EditChangelistSupport
import com.intellij.openapi.wm.ToolWindow
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.VcsDisposable
import com.intellij.vcs.changes.viewModel.ChangesViewProxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

class ChangesViewCommitPanel internal constructor(
  project: Project,
  private val changesView: ChangesViewProxy,
) : NonModalCommitPanel(project), ChangesViewCommitWorkflowUi {
  private var isHideToolWindowOnCommit = false

  private val progressPanel = CommitProgressPanel(project, this, commitMessage.editorField)
  private val commitActions = commitActionsPanel.createActions()
  private var rootComponent: JComponent? = null

  @ApiStatus.Internal
  var postCommitCallback: (() -> Unit)? = null

  init {
    Disposer.register(this, commitMessage)
    setProgressComponent(progressPanel)

    for (support in EditChangelistSupport.EP_NAME.getExtensionList(project)) {
      support.installSearch(commitMessage.editorField, commitMessage.editorField)
    }

    val scope = VcsDisposable.getInstance(project).coroutineScope.childScope("ChangesViewCommitPanel")
    Disposer.register(this) { scope.cancel() }
    changesView.inclusionChanged.onEach { writeIntentReadAction { fireInclusionChanged() } }.launchIn(scope)

    commitActionsPanel.isCommitButtonDefault = {
      !progressPanel.isDumbMode && UIUtil.isFocusAncestor(rootComponent ?: component)
    }
  }

  override val isActive: Boolean get() = component.isVisible

  @ApiStatus.Internal
  fun registerRootComponent(newRootComponent: JComponent) {
    logger<ChangesViewCommitPanel>().assertTrue(rootComponent == null)
    rootComponent = newRootComponent
    commitActions.forEach { it.registerCustomShortcutSet(newRootComponent, this) }
  }

  override fun expand(item: Any) {
    changesView.expand(item)
  }

  override fun select(item: Any) {
    changesView.select(item)
  }

  override fun selectFirst(items: Collection<Any>) {
    changesView.selectFirst(items)
  }

  override fun setCompletionContext(changeLists: List<LocalChangeList>) {
    commitMessage.setChangesSupplier(ChangeListChangesSupplier(changeLists))
  }

  override fun getDisplayedChanges(): List<Change> = changesView.getDisplayedChanges()
  override fun getIncludedChanges(): List<Change> = changesView.getIncludedChanges()

  override fun getDisplayedUnversionedFiles(): List<FilePath> =
    changesView.getDisplayedUnversionedFiles()

  override fun getIncludedUnversionedFiles(): List<FilePath> =
    changesView.getIncludedUnversionedFiles()

  override fun setInclusionModel(model: InclusionModel?) {
    changesView.setInclusionModel(model)
  }

  override val commitProgressUi: CommitProgressUi get() = progressPanel

  override fun endExecution() {
    postCommitCallback?.invoke()
  }

  override fun activate(): Boolean {
    val toolWindow = getVcsToolWindow() ?: return false
    val contentManager = ChangesViewContentManager.getInstance(project)

    saveToolWindowState()
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
    val deferred = CompletableDeferred<Unit>()
    changesView.scheduleRefreshNow { deferred.complete(Unit) }
    deferred.await()
  }
}

