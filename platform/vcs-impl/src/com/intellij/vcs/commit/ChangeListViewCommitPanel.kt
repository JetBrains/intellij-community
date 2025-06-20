// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesViewManager
import com.intellij.openapi.vcs.changes.InclusionModel
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.ui.ChangeListChangesSupplier
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.UNVERSIONED_FILES_TAG
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vcs.changes.ui.EditChangelistSupport
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData.*
import com.intellij.ui.EditorTextComponent
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil.*
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import kotlin.properties.Delegates.observable

abstract class ChangeListViewCommitPanel @ApiStatus.Internal constructor(
  project: Project,
  private val changesView: ChangesListView,
) : NonModalCommitPanel(project), ChangesViewCommitWorkflowUi {
  private val progressPanel = ChangeListViewCommitProgressPanel(project, this, commitMessage.editorField)

  private val commitActions = commitActionsPanel.createActions()
  private var rootComponent: JComponent? = null

  init {
    Disposer.register(this, commitMessage)

    setProgressComponent(progressPanel)

    for (support in EditChangelistSupport.EP_NAME.getExtensionList(project)) {
      support.installSearch(commitMessage.editorField, commitMessage.editorField)
    }

    changesView.setInclusionListener {
      //readaction is not enough
      WriteIntentReadAction.run { fireInclusionChanged() }
    }
    changesView.isShowCheckboxes = true

    commitActionsPanel.isCommitButtonDefault = {
      !progressPanel.isDumbMode &&
      UIUtil.isFocusAncestor(rootComponent ?: component)
    }
  }

  @ApiStatus.Internal
  fun registerRootComponent(newRootComponent: JComponent) {
    logger<ChangesViewCommitPanel>().assertTrue(rootComponent == null)
    rootComponent = newRootComponent
    commitActions.forEach { it.registerCustomShortcutSet(newRootComponent, this) }
  }

  final override var editedCommit: EditedCommitPresentation? by observable(null) { _, _, newValue ->
    ChangesViewManager.getInstanceEx(project).promiseRefresh().then {
      newValue?.let { expand(it) }
    }
  }

  final override fun expand(item: Any) {
    val node = changesView.findNodeInTree(item)
    node?.let { changesView.expandSafe(it) }
  }

  final override fun select(item: Any) {
    val path = changesView.findNodePathInTree(item)
    path?.let { selectPath(changesView, it, false) }
  }

  final override fun selectFirst(items: Collection<Any>) {
    if (items.isEmpty()) return

    val path = treePathTraverser(changesView).preOrderDfsTraversal().find { getLastUserObject(it) in items }
    path?.let { selectPath(changesView, it, false) }
  }

  final override fun setCompletionContext(changeLists: List<LocalChangeList>) {
    commitMessage.setChangesSupplier(ChangeListChangesSupplier(changeLists))
  }

  final override fun getDisplayedChanges(): List<Change> = all(changesView).userObjects(Change::class.java)
  final override fun getIncludedChanges(): List<Change> = included(changesView).userObjects(Change::class.java)

  final override fun getDisplayedUnversionedFiles(): List<FilePath> =
    allUnderTag(changesView, UNVERSIONED_FILES_TAG).userObjects(FilePath::class.java)

  final override fun getIncludedUnversionedFiles(): List<FilePath> =
    includedUnderTag(changesView, UNVERSIONED_FILES_TAG).userObjects(FilePath::class.java)

  final override var inclusionModel: InclusionModel?
    get() = changesView.inclusionModel
    set(value) {
      changesView.setInclusionModel(value)
    }

  final override val commitProgressUi: CommitProgressUi get() = progressPanel

  final override fun endExecution(): Unit = closeEditorPreviewIfEmpty()

  private fun closeEditorPreviewIfEmpty() {
    val changesViewManager = ChangesViewManager.getInstance(project) as? ChangesViewManager ?: return
    ChangesViewManager.getInstanceEx(project).promiseRefresh().then {
      changesViewManager.closeEditorPreview(true)
    }
  }

  final override fun dispose() {
    changesView.isShowCheckboxes = false
    changesView.setInclusionListener(null)
  }
}

private class ChangeListViewCommitProgressPanel(
  project: Project,
  private val commitWorkflowUi: ChangesViewCommitWorkflowUi,
  commitMessage: EditorTextComponent,
) : CommitProgressPanel(project) {

  private var oldInclusion: Set<Any> = emptySet()

  init {
    setup(commitWorkflowUi, commitMessage, JBUI.Borders.empty())
  }

  override fun inclusionChanged() {
    val newInclusion = commitWorkflowUi.inclusionModel?.getInclusion().orEmpty()

    if (oldInclusion != newInclusion) super.inclusionChanged()
    oldInclusion = newInclusion
  }
}
