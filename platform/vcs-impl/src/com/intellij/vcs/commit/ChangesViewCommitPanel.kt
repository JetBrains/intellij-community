// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.UNVERSIONED_FILES_TAG
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.LOCAL_CHANGES
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.getToolWindowFor
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData.*
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.EditorTextComponent
import com.intellij.ui.IdeBorderFactory.createBorder
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.util.ui.JBUI.Borders.*
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.tree.TreeUtil.*
import com.intellij.vcsUtil.VcsUtil.getFilePath
import javax.swing.JComponent
import javax.swing.SwingConstants
import kotlin.properties.Delegates.observable

internal fun ChangesBrowserNode<*>.subtreeRootObject(): Any? = (path.getOrNull(1) as? ChangesBrowserNode<*>)?.userObject

class ChangesViewCommitPanel(private val changesViewHost: ChangesViewPanel, private val rootComponent: JComponent) :
  NonModalCommitPanel(changesViewHost.changesView.project), ChangesViewCommitWorkflowUi {

  val changesView get() = changesViewHost.changesView

  private val toolbarPanel = simplePanel().apply {
    isOpaque = false
    border = emptyLeft(1)
  }
  private val progressPanel = ChangesViewCommitProgressPanel(this, commitMessage.editorField)

  private var isHideToolWindowOnDeactivate = false

  var isToolbarHorizontal: Boolean by observable(false) { _, oldValue, newValue ->
    if (oldValue != newValue) {
      addToolbar(newValue) // this also removes toolbar from previous parent
    }
  }

  init {
    Disposer.register(this, commitMessage)

    bottomPanel = {
      add(progressPanel.apply { border = empty(6) })
      add(commitAuthorComponent.apply { border = empty(0, 5, 4, 0) })
      add(commitActionsPanel)
    }
    buildLayout()
    addToolbar(isToolbarHorizontal)

    for (support in EditChangelistSupport.EP_NAME.getExtensions(project)) {
      support.installSearch(commitMessage.editorField, commitMessage.editorField)
    }

    with(changesView) {
      setInclusionListener { fireInclusionChanged() }
      isShowCheckboxes = true
    }
    changesViewHost.statusComponent =
      CommitStatusPanel(this).apply {
        border = emptyRight(6)
        background = changesView.background

        addToLeft(toolbarPanel)
      }
    ChangesViewCommitTabTitleUpdater(this).start()

    commitActionsPanel.setupShortcuts(rootComponent, this)
    commitActionsPanel.isCommitButtonDefault = {
      !progressPanel.isDumbMode &&
      IdeFocusManager.getInstance(project).getFocusedDescendantFor(rootComponent) != null
    }
  }

  private fun addToolbar(isHorizontal: Boolean) {
    if (isHorizontal) {
      toolbar.setOrientation(SwingConstants.HORIZONTAL)
      toolbar.setReservePlaceAutoPopupIcon(false)

      centerPanel.border = null
      toolbarPanel.addToCenter(toolbar.component)
    }
    else {
      toolbar.setOrientation(SwingConstants.VERTICAL)
      toolbar.setReservePlaceAutoPopupIcon(true)

      centerPanel.border = createBorder(JBColor.border(), SideBorder.LEFT)
      addToLeft(toolbar.component)
    }
  }

  override var editedCommit by observable<EditedCommitDetails?>(null) { _, _, newValue ->
    refreshData()
    newValue?.let { expand(it) }
  }

  override val isActive: Boolean get() = isVisible

  override fun activate(): Boolean {
    val toolWindow = getVcsToolWindow() ?: return false
    val contentManager = ChangesViewContentManager.getInstance(project)

    saveToolWindowState()
    changesView.isShowCheckboxes = true
    isVisible = true
    commitActionsPanel.isActive = true

    contentManager.selectContent(LOCAL_CHANGES)
    toolWindow.activate({ commitMessage.requestFocusInMessage() }, false)
    return true
  }

  override fun deactivate(isRestoreState: Boolean) {
    if (isRestoreState) restoreToolWindowState()
    clearToolWindowState()
    changesView.isShowCheckboxes = false
    isVisible = false
    commitActionsPanel.isActive = false
  }

  private fun saveToolWindowState() {
    if (!isActive) {
      isHideToolWindowOnDeactivate = getVcsToolWindow()?.isVisible != true
    }
  }

  private fun restoreToolWindowState() {
    if (isHideToolWindowOnDeactivate) {
      getVcsToolWindow()?.hide(null)
    }
  }

  private fun clearToolWindowState() {
    isHideToolWindowOnDeactivate = false
  }

  private fun getVcsToolWindow(): ToolWindow? = getToolWindowFor(project, LOCAL_CHANGES)

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

  override fun showCommitOptions(popup: JBPopup, isFromToolbar: Boolean, dataContext: DataContext) =
    if (isFromToolbar && !isToolbarHorizontal) popup.showAbove(this@ChangesViewCommitPanel)
    else super.showCommitOptions(popup, isFromToolbar, dataContext)

  override fun setCompletionContext(changeLists: List<LocalChangeList>) {
    commitMessage.changeLists = changeLists
  }

  override fun refreshData() = ChangesViewManager.getInstanceEx(project).refreshImmediately()

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

  override fun includeIntoCommit(items: Collection<*>) = changesView.includeChanges(items)

  override val commitProgressUi: CommitProgressUi get() = progressPanel

  override fun endExecution() = closeEditorPreviewIfEmpty()

  private fun closeEditorPreviewIfEmpty() {
    val changesViewManager = ChangesViewManager.getInstance(project) as? ChangesViewManager ?: return
    if (!changesViewManager.isEditorPreview) return

    refreshData()
    changesViewManager.closeEditorPreview(true)
  }

  override fun dispose() {
    changesViewHost.statusComponent = null
    with(changesView) {
      isShowCheckboxes = false
      setInclusionListener(null)
    }
  }
}

private class ChangesViewCommitProgressPanel(
  private val commitWorkflowUi: ChangesViewCommitWorkflowUi,
  commitMessage: EditorTextComponent
) : CommitProgressPanel() {

  private var oldInclusion: Set<Any> = emptySet()

  init {
    setup(commitWorkflowUi, commitMessage)
  }

  override fun inclusionChanged() {
    val newInclusion = commitWorkflowUi.inclusionModel?.getInclusion().orEmpty()

    if (oldInclusion != newInclusion) super.inclusionChanged()
    oldInclusion = newInclusion
  }
}

private class ChangesViewCommitTabTitleUpdater(private val commitPanel: ChangesViewCommitPanel) :
  CommitTabTitleUpdater(commitPanel.changesView, LOCAL_CHANGES, { message("local.changes.tab") }),
  ChangesViewContentManagerListener {

  init {
    Disposer.register(commitPanel, this)

    pathsProvider = {
      val singleRoot = ProjectLevelVcsManager.getInstance(project).allVersionedRoots.singleOrNull()
      if (singleRoot != null) listOf(getFilePath(singleRoot)) else commitPanel.getDisplayedPaths()
    }
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