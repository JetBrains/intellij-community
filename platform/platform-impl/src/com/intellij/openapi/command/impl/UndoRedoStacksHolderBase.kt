// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

internal abstract class UndoRedoStacksHolderBase<E>(protected val isUndo: Boolean) : StacksHolderBase<E, UndoRedoList<E>>() {

  fun getStacksDescription(): String {
    return if (isUndo) "undo stacks" else "redo stacks"
  }

  override fun newCollection(): UndoRedoList<E> {
    return UndoRedoList<E>()
  }
}