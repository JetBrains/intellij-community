// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.ExpandableItemsHandler
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.Processor
import com.intellij.util.ui.UIUtil
import java.awt.event.MouseEvent
import javax.swing.JComponent

abstract class ChangesTreeEditorDiffPreview(
  protected val tree: ChangesTree,
  targetComponent: JComponent = tree,
) : EditorTabDiffPreview(tree.project) {

  init {
    tree.doubleClickHandler = Processor { e -> handleDoubleClick(e) }
    tree.enterKeyHandler = Processor { e -> handleEnterKey() }
    tree.addSelectionListener { handleSingleClick() }
    PreviewOnNextDiffAction().registerCustomShortcutSet(targetComponent, this)

    UIUtil.putClientProperty(tree, ExpandableItemsHandler.IGNORE_ITEM_SELECTION, true)
  }

  protected open fun isPreviewOnDoubleClick(): Boolean = true
  open fun handleDoubleClick(e: MouseEvent): Boolean {
    if (!isPreviewOnDoubleClick()) return false
    if (EditSourceOnDoubleClickHandler.isToggleEvent(tree, e)) return false
    return performDiffAction()
  }

  protected open fun isPreviewOnEnter(): Boolean = true
  open fun handleEnterKey(): Boolean {
    if (!isPreviewOnEnter()) return false
    return performDiffAction()
  }

  protected open fun isOpenPreviewWithSingleClickEnabled(): Boolean = false
  protected open fun isOpenPreviewWithSingleClick(): Boolean {
    if (!isOpenPreviewWithSingleClickEnabled()) return false
    if (ToolWindowManager.getInstance(project).isEditorComponentActive) return false
    if (tree != IdeFocusManager.getInstance(tree.project).focusOwner) return false
    return true
  }

  protected open fun handleSingleClick() {
    if (!isOpenPreviewWithSingleClick()) return
    val opened = openPreview(false)
    if (!opened) {
      closePreview() // auto-close editor tab if nothing to preview
    }
  }

  protected open fun isOpenPreviewWithNextDiffShortcut(): Boolean = true
  protected open fun handleNextDiffShortcut() {
    if (!isOpenPreviewWithNextDiffShortcut()) return
    openPreview(true)
  }

  override fun handleEscapeKey() {
    closePreview()
    returnFocusToTree()
  }

  protected open fun returnFocusToTree() = Unit

  private inner class PreviewOnNextDiffAction : DumbAwareAction() {
    init {
      copyShortcutFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_DIFF))
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = isOpenPreviewWithNextDiffShortcut()
    }

    override fun actionPerformed(e: AnActionEvent) {
      handleNextDiffShortcut()
    }
  }
}
