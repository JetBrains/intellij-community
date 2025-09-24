// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesViewManager
import com.intellij.openapi.vcs.changes.InclusionModel
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.UNVERSIONED_FILES_TAG
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.LOCAL_CHANGES
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.getToolWindowFor
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData.*
import com.intellij.openapi.wm.ToolWindow
import com.intellij.platform.vcs.impl.shared.commit.EditedCommitPresentation
import com.intellij.util.application
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil.*
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import kotlin.properties.Delegates.observable

class ChangesViewCommitPanel @ApiStatus.Internal constructor(
  project: Project,
  private val changesView: ChangesListView,
) : NonModalCommitPanel(project), ChangesViewCommitWorkflowUi {
  private var isHideToolWindowOnCommit = false

  private val progressPanel = ChangeListViewCommitProgressPanel(project, this, commitMessage.editorField)
  private val commitActions = commitActionsPanel.createActions()
  private var rootComponent: JComponent? = null

  init {
    val titleUpdater = ChangesViewCommitTabTitleUpdater(project)
    titleUpdater.initSubscription(this)
    titleUpdater.updateTitle()

    Disposer.register(this, commitMessage)
    setProgressComponent(progressPanel)

    for (support in EditChangelistSupport.EP_NAME.getExtensionList(project)) {
      support.installSearch(commitMessage.editorField, commitMessage.editorField)
    }

    changesView.setInclusionListener {
      WriteIntentReadAction.run { fireInclusionChanged() }
    }
    changesView.isShowCheckboxes = true

    commitActionsPanel.isCommitButtonDefault = {
      !progressPanel.isDumbMode && UIUtil.isFocusAncestor(rootComponent ?: component)
    }

    ChangesViewCommitTabTitleUpdater(project).initSubscription(this)
  }

  override val isActive: Boolean get() = component.isVisible

  @ApiStatus.Internal
  fun registerRootComponent(newRootComponent: JComponent) {
    logger<ChangesViewCommitPanel>().assertTrue(rootComponent == null)
    rootComponent = newRootComponent
    commitActions.forEach { it.registerCustomShortcutSet(newRootComponent, this) }
  }

  override var editedCommit: EditedCommitPresentation? by observable(null) { _, _, newValue ->
    ChangesViewManager.getInstanceEx(project).scheduleRefresh {
      application.invokeLater { newValue?.let { expand(it) } }
    }
  }

  override fun expand(item: Any) {
    val node = changesView.findNodeInTree(item)
    node?.let { changesView.expandSafe(it) }
  }

  override fun select(item: Any) {
    val path = changesView.findNodePathInTree(item)
    path?.let { selectPath(changesView, it, false) }
  }

  override fun selectFirst(items: Collection<Any>) {
    if (items.isEmpty()) return

    val path = treePathTraverser(changesView).preOrderDfsTraversal().find { getLastUserObject(it) in items }
    path?.let { selectPath(changesView, it, false) }
  }

  override fun setCompletionContext(changeLists: List<LocalChangeList>) {
    commitMessage.setChangesSupplier(ChangeListChangesSupplier(changeLists))
  }

  override fun getDisplayedChanges(): List<Change> = all(changesView).userObjects(Change::class.java)
  override fun getIncludedChanges(): List<Change> = included(changesView).userObjects(Change::class.java)

  override fun getDisplayedUnversionedFiles(): List<FilePath> =
    allUnderTag(changesView, UNVERSIONED_FILES_TAG).userObjects(FilePath::class.java)

  override fun getIncludedUnversionedFiles(): List<FilePath> =
    includedUnderTag(changesView, UNVERSIONED_FILES_TAG).userObjects(FilePath::class.java)

  override var inclusionModel: InclusionModel?
    get() = changesView.inclusionModel
    set(value) {
      changesView.setInclusionModel(value)
    }

  override val commitProgressUi: CommitProgressUi get() = progressPanel

  override fun endExecution(): Unit = closeEditorPreviewIfEmpty()

  private fun closeEditorPreviewIfEmpty() {
    val changesViewManager = ChangesViewManager.getInstance(project) as? ChangesViewManager ?: return
    ChangesViewManager.getInstanceEx(project).scheduleRefresh {
      application.invokeLater {
        changesViewManager.closeEditorPreview(true)
      }
    }
  }

  override fun dispose() {
    super.dispose()
    changesView.isShowCheckboxes = false
    changesView.setInclusionListener(null)
  }

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
    val deferred = CompletableDeferred<Unit>()
    ChangesViewManager.getInstanceEx(project).scheduleRefresh { deferred.complete(Unit) }
    deferred.await()
  }
}

private class ChangesViewCommitTabTitleUpdater(private val project: Project) : ChangesViewContentManagerListener {
  fun initSubscription(disposable: Disposable) {
    project.messageBus.connect(disposable).subscribe(ChangesViewContentManagerListener.TOPIC, this)
  }

  override fun toolWindowMappingChanged() {
    updateTitle()
  }

  fun updateTitle() {
    val tabContent = ChangesViewContentManager.getInstance(project).findContent(LOCAL_CHANGES)
    if (tabContent != null) {
      val contentsCount = getToolWindowFor(project, LOCAL_CHANGES)?.contentManager?.contentCount ?: 0

      tabContent.displayName = when {
        contentsCount == 1 -> null
        project.isCommitToolWindowShown -> message("tab.title.commit")
        else ->message("local.changes.tab")
    }}
  }
}
