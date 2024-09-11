// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf

import com.intellij.ide.DataManager
import com.intellij.ide.actions.EditSourceAction
import com.intellij.ide.dnd.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry.Companion.get
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.DiffPreview
import com.intellij.openapi.vcs.changes.DiffPreview.Companion.setPreviewVisible
import com.intellij.openapi.vcs.changes.EditorTabDiffPreviewManager
import com.intellij.openapi.vcs.changes.PreviewDiffSplitterComponent
import com.intellij.openapi.vcs.changes.actions.ShowDiffPreviewAction
import com.intellij.openapi.vcs.changes.shelf.DiffShelvedChangesActionProvider
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.getToolWindowFor
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.isToolWindowTabVertical
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.Function
import com.intellij.util.ObjectUtils
import com.intellij.util.ui.JBUI
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.event.CellEditorListener
import javax.swing.event.ChangeEvent
import javax.swing.tree.DefaultTreeCellEditor
import javax.swing.tree.TreeCellEditor
import javax.swing.tree.TreeNode

class ShelfToolWindowPanel internal constructor(val project: Project) : SimpleToolWindowPanel(true), Disposable {
  val tree: ShelfTree = ShelfTree(project)
  private val shelveChangesManager: ShelveChangesManager = ShelveChangesManager.getInstance(project)

  private val vcsConfiguration: VcsConfiguration = VcsConfiguration.getInstance(project)
  private val mainPanelContent = Wrapper()
  private var shelvePanel: JPanel
  private val scrollPane: JScrollPane

  private val editorDiffPreview: ShelveEditorDiffPreview
  private var splitterDiffPreview: ShelveSplitterDiffPreview? = null

  private var disposed = false

  init {
    shelveChangesManager
    vcsConfiguration

    tree.setEditable(true)
    tree.setDragEnabled(!ApplicationManager.getApplication().isHeadlessEnvironment())
    tree.setCellEditor(ShelveRenameTreeCellEditor())

    val showDiffAction = ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_DIFF_COMMON)
    showDiffAction.registerCustomShortcutSet(showDiffAction.shortcutSet, tree)
    val editSourceAction = EditSourceAction()
    editSourceAction.registerCustomShortcutSet(editSourceAction.shortcutSet, tree)

    val actionGroup = DefaultActionGroup()
    actionGroup.addAll(ActionManager.getInstance().getAction(SHELVED_CHANGES_TOOLBAR) as ActionGroup)
    actionGroup.add(Separator.getInstance())
    actionGroup.add(MyToggleDetailsAction())

    val toolbar = ActionManager.getInstance().createActionToolbar("ShelvedChanges", actionGroup, true)
    toolbar.setTargetComponent(tree)
    scrollPane = ScrollPaneFactory.createScrollPane(tree, true)
    shelvePanel = JBUI.Panels.simplePanel(scrollPane).addToTop(toolbar.component)
    mainPanelContent.setContent(shelvePanel)
    setContent(mainPanelContent)
    updatePanelLayout()

    editorDiffPreview = ShelveEditorDiffPreview()
    Disposer.register(this, editorDiffPreview)

    project.getMessageBus().connect(this).subscribe(ChangesViewContentManagerListener.TOPIC, object : ChangesViewContentManagerListener {
      override fun toolWindowMappingChanged() {
        updatePanelLayout()
      }
    })
    PopupHandler.installPopupMenu(tree, "ShelvedChangesPopupMenu", SHELF_CONTEXT_MENU)
    MyDnDSupport(project, tree, scrollPane).install(this)
  }

  override fun dispose() {
    disposed = true

    if (splitterDiffPreview != null) Disposer.dispose(splitterDiffPreview!!)
    splitterDiffPreview = null

    tree.shutdown()
  }

  private fun updatePanelLayout() {
    val isVertical = isToolWindowTabVertical(project, ChangesViewContentManager.SHELF)
    val hasSplitterPreview = !isVertical
    val needUpdatePreview = hasSplitterPreview != (splitterDiffPreview != null)
    if (!needUpdatePreview) return

    if (hasSplitterPreview) {
      splitterDiffPreview = ShelveSplitterDiffPreview()
      setPreviewVisible(splitterDiffPreview!!, vcsConfiguration.SHELVE_DETAILS_PREVIEW_SHOWN)
    }
    else {
      Disposer.dispose(splitterDiffPreview!!)
      splitterDiffPreview = null
    }
  }


  private inner class ShelveEditorDiffPreview : TreeHandlerEditorDiffPreview(tree, scrollPane, ShelveTreeDiffPreviewHandler.INSTANCE) {
    override fun createViewer(): com.intellij.diff.impl.DiffEditorViewer {
      return ShelvedPreviewProcessor(project, this@ShelfToolWindowPanel.tree, true)
    }

    public override fun returnFocusToTree() {
      getToolWindowFor(project, ChangesViewContentManager.SHELF)?.activate(null)
    }

    override fun updateDiffAction(event: AnActionEvent) {
      DiffShelvedChangesActionProvider.updateAvailability(event)
    }

    override fun getEditorTabName(wrapper: ChangeViewDiffRequestProcessor.Wrapper?): String {
      return if (wrapper != null)
        VcsBundle.message("shelve.editor.diff.preview.title", wrapper.getPresentableName())
      else
        VcsBundle.message("shelved.version.name")
    }

    override fun isOpenPreviewWithSingleClickEnabled(): Boolean {
      return isOpenEditorDiffPreviewWithSingleClick.asBoolean()
    }

    override fun isOpenPreviewWithSingleClick(): Boolean {
      if (splitterDiffPreview != null && vcsConfiguration.SHELVE_DETAILS_PREVIEW_SHOWN) return false
      return super.isOpenPreviewWithSingleClick()
    }
  }

  private inner class ShelveSplitterDiffPreview : DiffPreview, Disposable {
    private val myProcessor: ShelvedPreviewProcessor
    private val mySplitterComponent: PreviewDiffSplitterComponent

    init {
      myProcessor = ShelvedPreviewProcessor(project, tree, false)
      mySplitterComponent = PreviewDiffSplitterComponent(myProcessor, SHELVE_PREVIEW_SPLITTER_PROPORTION)

      mySplitterComponent.firstComponent = scrollPane
      this@ShelfToolWindowPanel.setContent(mySplitterComponent)
    }

    override fun dispose() {
      Disposer.dispose(myProcessor)

      if (!this@ShelfToolWindowPanel.disposed) {
        this@ShelfToolWindowPanel.setContent(scrollPane)
      }
    }

    override fun openPreview(requestFocus: Boolean): Boolean {
      return mySplitterComponent.openPreview(requestFocus)
    }

    override fun closePreview() {
      mySplitterComponent.closePreview()
    }
  }

  private class MyDnDSupport(
    val project: Project,
    val tree: ChangesTree,
    val treeScrollPane: JScrollPane,
  ) : DnDDropHandler, DnDTargetChecker {
    fun install(disposable: Disposable) {
      DnDSupport.createBuilder(tree)
        .setTargetChecker(this)
        .setDropHandler(this)
        .setImageProvider(Function { info: DnDActionInfo? -> this.createDraggedImage(info!!) })
        .setBeanProvider(Function { info: DnDActionInfo? -> this.createDragStartBean(info!!) })
        .setDisposableParent(disposable)
        .install()
    }

    override fun drop(aEvent: DnDEvent) {
      ShelvedChangesViewManager.handleDropEvent(project, aEvent)
    }

    override fun update(aEvent: DnDEvent): Boolean {
      aEvent.hideHighlighter()
      aEvent.setDropPossible(false, "")

      val canHandle = ShelvedChangesViewManager.canHandleDropEvent(project, aEvent)
      if (!canHandle) return true

      // highlight top of the tree
      val tableCellRect = Rectangle(0, 0, JBUI.scale(300), JBUI.scale(12))
      aEvent.setHighlighting(com.intellij.ui.awt.RelativeRectangle(treeScrollPane, tableCellRect), DnDEvent.DropTargetHighlightingType.RECTANGLE)
      aEvent.setDropPossible(true)

      return false
    }

    fun createDragStartBean(info: DnDActionInfo): DnDDragStartBean? {
      if (info.isMove) {
        val dc = DataManager.getInstance().getDataContext(tree)
        return DnDDragStartBean(ShelvedChangeListDragBean(ShelvedChangesViewManager.getShelveChanges(dc),
                                                          ShelvedChangesViewManager.getBinaryShelveChanges(dc),
                                                          ShelvedChangesViewManager.getShelvedLists(dc)))
      }
      return null
    }

    fun createDraggedImage(info: DnDActionInfo): DnDImage {
      val imageText = VcsBundle.message("unshelve.changes.action")
      return ChangesTreeDnDSupport.createDragImage(tree, imageText)
    }
  }

  override fun uiDataSnapshot(sink: DataSink) {
    super.uiDataSnapshot(sink)
    sink.set<DiffPreview>(EditorTabDiffPreviewManager.EDITOR_TAB_DIFF_PREVIEW, editorDiffPreview)
  }

  private inner class MyToggleDetailsAction : ShowDiffPreviewAction() {
    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.setEnabledAndVisible(splitterDiffPreview != null || isOpenEditorDiffPreviewWithSingleClick.asBoolean())
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      val previewSplitter: DiffPreview = ObjectUtils.chooseNotNull(splitterDiffPreview, editorDiffPreview)
      setPreviewVisible(previewSplitter, state)
      vcsConfiguration.SHELVE_DETAILS_PREVIEW_SHOWN = state
    }

    override fun isSelected(e: AnActionEvent): Boolean {
      return vcsConfiguration.SHELVE_DETAILS_PREVIEW_SHOWN
    }
  }

  private inner class ShelveRenameTreeCellEditor : DefaultTreeCellEditor(tree, null), CellEditorListener {
    init {
      addCellEditorListener(this)
    }

    override fun isCellEditable(event: EventObject?): Boolean {
      return event !is MouseEvent && super.isCellEditable(event)
    }

    override fun editingStopped(e: ChangeEvent) {
      val node = this@ShelfToolWindowPanel.tree.getLastSelectedPathComponent() as TreeNode?
      val source = e.source
      if (node is ShelvedListNode && source is TreeCellEditor
      ) {
        val editorValue: String = source.cellEditorValue.toString()
        val shelvedChangeList = node.changeList
        shelveChangesManager.renameChangeList(shelvedChangeList, editorValue)
      }
    }

    override fun editingCanceled(e: ChangeEvent?) {
    }
  }

  companion object {
    private const val SHELVED_CHANGES_TOOLBAR = "ShelvedChangesToolbar"
    private const val SHELF_CONTEXT_MENU = "Vcs.Shelf.ContextMenu"
    private const val SHELVE_PREVIEW_SPLITTER_PROPORTION = "ShelvedChangesViewManager.DETAILS_SPLITTER_PROPORTION"

    private val isOpenEditorDiffPreviewWithSingleClick = get("show.diff.preview.as.editor.tab.with.single.click")
  }
}