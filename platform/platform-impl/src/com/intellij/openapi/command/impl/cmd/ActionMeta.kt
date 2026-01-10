// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl.cmd

import com.intellij.openapi.command.impl.UndoableActionType
import com.intellij.openapi.command.undo.DocumentReference


interface ActionMeta {
  fun type(): UndoableActionType
  fun affectedDocuments(): Collection<DocumentReference>?
  fun isGlobal(): Boolean

  companion object {
    @JvmStatic
    fun create(type: UndoableActionType, affectedDocuments: Array<DocumentReference>?, isGlobal: Boolean): ActionMeta {
      return ActionMetaImpl(type, affectedDocuments?.toList(), isGlobal)
    }
  }
}

private class ActionMetaImpl(
  private val type: UndoableActionType,
  private val affectedDocuments: Collection<DocumentReference>?,
  private val isGlobal: Boolean,
) : ActionMeta {
  override fun type(): UndoableActionType = type
  override fun affectedDocuments(): Collection<DocumentReference>? = affectedDocuments
  override fun isGlobal(): Boolean = isGlobal
}
