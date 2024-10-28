// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.frontend.shelf

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.impl.frontend.changes.EDITOR_TAB_DIFF_PREVIEW
import com.intellij.vcs.impl.frontend.changes.actions.SHOW_DIFF_ACTION_ID
import com.intellij.vcs.impl.frontend.navigation.FrontendNavigateToSourceAction
import com.intellij.vcs.impl.frontend.shelf.tree.ShelfTree
import com.intellij.vcs.impl.frontend.shelf.tree.ShelfTreeEditorDiffPreview
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.NonNls
import javax.swing.JScrollPane

class ShelfToolWindowPanel(project: Project, tree: ShelfTree, cs: CoroutineScope) : SimpleToolWindowPanel(true) {
  private val mainPanelContent = Wrapper()
  private val scrollPane: JScrollPane = ScrollPaneFactory.createScrollPane(tree, true)
  private var shelvePanel: BorderLayoutPanel = JBUI.Panels.simplePanel(scrollPane)
  private val diffEditorPreview = ShelfTreeEditorDiffPreview(tree, cs, project)

  init {
    mainPanelContent.setContent(shelvePanel)
    setContent(mainPanelContent)
    val actionGroup = DefaultActionGroup()
    val actionManager = ActionManager.getInstance()
    actionGroup.addAll(actionManager.getAction(SHELVED_CHANGES_TOOLBAR_ID) as ActionGroup)
    actionGroup.add(Separator.getInstance())
    val showDiffAction = actionManager.getAction(SHOW_DIFF_ACTION_ID)
    showDiffAction.registerCustomShortcutSet(showDiffAction.shortcutSet, tree)
    val editSourceAction = FrontendNavigateToSourceAction()
    editSourceAction.registerCustomShortcutSet(editSourceAction.shortcutSet, tree)
    val toolbar = actionManager.createActionToolbar("ShelvedChanges", actionGroup, true)
    toolbar.setTargetComponent(tree)
    shelvePanel.addToTop(toolbar.component)
    tree.isEditable = true
    tree.cellEditor = ShelfRenameTreeCellEditor(tree)

    PopupHandler.installPopupMenu(tree, "ShelvedChangesPopupMenuFrontend", SHELF_CONTEXT_MENU);
  }

  override fun uiDataSnapshot(sink: DataSink) {
    super.uiDataSnapshot(sink);
    sink[EDITOR_TAB_DIFF_PREVIEW] = diffEditorPreview;
  }

  companion object {
    private const val SHELVED_CHANGES_TOOLBAR_ID = "ShelvedChangesToolbarFrontend"
    private const val GROUP_BY_GROUP_ID = "ChangesView.GroupBy"

    @NonNls
    const val SHELF_CONTEXT_MENU = "Frontend.Vcs.Shelf.ContextMenu"
  }
}