// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.shelf

import com.intellij.platform.vcs.impl.frontend.shelf.tree.ShelfTree
import com.intellij.platform.vcs.impl.frontend.shelf.tree.ShelvedChangeListNode
import org.jetbrains.annotations.ApiStatus
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.event.CellEditorListener
import javax.swing.event.ChangeEvent
import javax.swing.tree.DefaultTreeCellEditor
import javax.swing.tree.TreeCellEditor

@ApiStatus.Internal
class ShelfRenameTreeCellEditor(private val shelfTree: ShelfTree) : DefaultTreeCellEditor(shelfTree, null), CellEditorListener {
  init {
    addCellEditorListener(this)
  }

  override fun editingStopped(e: ChangeEvent?) {
    val node = tree.lastSelectedPathComponent as? ShelvedChangeListNode ?: return
    val cellEditor = e?.source as? TreeCellEditor ?: return
    val editorValue = cellEditor.cellEditorValue.toString()
    ShelfService.getInstance(shelfTree.project).renameChangeList(node.userObject, editorValue)
  }

  override fun editingCanceled(e: ChangeEvent?) {
  }

  override fun isCellEditable(event: EventObject?): Boolean {
    return event !is MouseEvent && super.isCellEditable(event)
  }
}