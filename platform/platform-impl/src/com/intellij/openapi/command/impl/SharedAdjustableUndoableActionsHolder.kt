// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.command.undo.AdjustableUndoableAction
import com.intellij.openapi.command.undo.ImmutableActionChangeRange

internal class SharedAdjustableUndoableActionsHolder : StacksHolderBase<AdjustableUndoableAction, UndoRedoSet<AdjustableUndoableAction>>() {
  override fun newCollection(): UndoRedoSet<AdjustableUndoableAction> {
    return UndoRedoSet()
  }

  fun contains(range: ImmutableActionChangeRange): Boolean {
    val action = range.actionReference.get() ?: return false

    val affectedDocuments = action.affectedDocuments ?: return false
    return affectedDocuments.any { getStack(it).contains(action) }
  }

  fun addAction(action: AdjustableUndoableAction) {
    val documentReferences = action.affectedDocuments ?: return
    documentReferences.forEach {
      getStack(it).add(action)
    }
  }

  fun remove(action: AdjustableUndoableAction) {
    val documentReferences = action.affectedDocuments ?: return
    documentReferences.forEach {
      getStack(it).remove(action)
    }
  }
}
