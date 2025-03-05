// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.shelf

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.DiffPreview
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.platform.vcs.impl.frontend.VcsFrontendConfiguration
import com.intellij.platform.vcs.impl.frontend.changes.EDITOR_TAB_DIFF_PREVIEW
import com.intellij.platform.vcs.impl.frontend.changes.actions.SHOW_DIFF_ACTION_ID
import com.intellij.platform.vcs.impl.frontend.navigation.FrontendNavigateToSourceAction
import com.intellij.platform.vcs.impl.frontend.shelf.tree.ShelfTree
import com.intellij.platform.vcs.impl.frontend.shelf.tree.ShelfTreeEditorDiffPreview
import com.intellij.platform.vcs.impl.shared.changes.PreviewDiffSplitterComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import javax.swing.JScrollPane

/*
 * Remote-dev friendly implementation of com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager.ShelfToolWindowPanel
 */
@ApiStatus.Internal
class ShelfToolWindowPanel(private val project: Project, tree: ShelfTree, cs: CoroutineScope) : SimpleToolWindowPanel(true) {
  private val mainPanelContent = Wrapper()
  private val scrollPane: JScrollPane = ScrollPaneFactory.createScrollPane(tree, true)
  private var shelvePanel: BorderLayoutPanel = JBUI.Panels.simplePanel(scrollPane)
  private val diffEditorPreview = ShelfTreeEditorDiffPreview(tree, cs, project)
  private var splitterPreview: ShelveSplitterDiffPreview? = null

  private val vcsConfiguration = VcsFrontendConfiguration.getInstance(project)

  init {
    mainPanelContent.setContent(shelvePanel)
    setContent(mainPanelContent)
    val actionGroup = DefaultActionGroup()
    val actionManager = ActionManager.getInstance()
    actionGroup.addAll(actionManager.getAction(SHELVED_CHANGES_TOOLBAR_ID) as ActionGroup)
    actionGroup.add(Separator.getInstance())
    actionGroup.add(TogglePreviewAction())
    val showDiffAction = actionManager.getAction(SHOW_DIFF_ACTION_ID)
    showDiffAction.registerCustomShortcutSet(showDiffAction.shortcutSet, tree)
    val editSourceAction = FrontendNavigateToSourceAction()
    editSourceAction.registerCustomShortcutSet(editSourceAction.shortcutSet, tree)
    val toolbar = actionManager.createActionToolbar("ShelvedChanges", actionGroup, true)
    toolbar.setTargetComponent(tree)
    shelvePanel.addToTop(toolbar.component)
    tree.isEditable = true
    tree.cellEditor = ShelfRenameTreeCellEditor(tree)
    cs.launch {
      subscribeToDiffPreviewChanged(project, cs) {
        val splitterComponent = it.splitter
        if (!isPanelVertical()) {
          cs.launch(Dispatchers.EDT) {
            splitterPreview = ShelveSplitterDiffPreview(splitterComponent)
            DiffPreview.setPreviewVisible(splitterPreview!!, vcsConfiguration.shelveDetailsPreviewShown)
          }
        }
      }
    }
    project.messageBus.connect(cs).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {

      override fun stateChanged(toolWindowManager: ToolWindowManager) = updatePanelLayout()
    })

    PopupHandler.installPopupMenu(tree, "ShelvedChangesPopupMenuFrontend", SHELF_CONTEXT_MENU);
  }


  private fun updatePanelLayout() {
    val hasSplitterPreview = !isPanelVertical()
    val needUpdatePreview = hasSplitterPreview != (splitterPreview != null)
    if (!needUpdatePreview) return

    val shelfService = ShelfService.getInstance(project)
    if (!hasSplitterPreview) {
      splitterPreview = null
      shelfService.deleteSplitterPreview()
    }
    else {
      shelfService.createPreviewDiffSplitter()
    }
  }

  private fun isPanelVertical(): Boolean {
    return ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.COMMIT)?.anchor?.isHorizontal != true
  }

  override fun uiDataSnapshot(sink: DataSink) {
    super.uiDataSnapshot(sink)
    sink[EDITOR_TAB_DIFF_PREVIEW] = diffEditorPreview;
  }

  inner class ShelveSplitterDiffPreview(val splitterComponent: PreviewDiffSplitterComponent) : DiffPreview {
    init {
      splitterComponent.firstComponent = shelvePanel
      mainPanelContent.setContent(splitterComponent)
    }

    override fun openPreview(requestFocus: Boolean): Boolean {
      return splitterComponent.openPreview(requestFocus)
    }

    override fun closePreview() {
      splitterComponent.closePreview()
    }
  }

  inner class TogglePreviewAction() : ToggleAction() {

    override fun isSelected(e: AnActionEvent): Boolean {
      return vcsConfiguration.shelveDetailsPreviewShown
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      val preview = splitterPreview
      if (preview != null) {
        DiffPreview.setPreviewVisible(preview, state)
        vcsConfiguration.shelveDetailsPreviewShown = state
      }
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isEnabledAndVisible = isOpenEditorDiffPreviewWithSingleClick.asBoolean() || !isPanelVertical()
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }
  }

  companion object {
    private const val SHELVED_CHANGES_TOOLBAR_ID = "ShelvedChangesToolbarFrontend"
    private val isOpenEditorDiffPreviewWithSingleClick = Registry.get("show.diff.preview.as.editor.tab.with.single.click");

    @NonNls
    const val SHELF_CONTEXT_MENU: String = "Frontend.Vcs.Shelf.ContextMenu"
  }
}