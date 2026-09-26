// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.UndoableAction
import com.intellij.openapi.util.NlsContexts.Command
import kotlinx.collections.immutable.PersistentList
import java.lang.ref.Reference

internal class UndoRedoListSnapshot<T>(val snapshot: PersistentList<T>) {
  fun toList(): UndoRedoList<T> = UndoRedoList(snapshot.builder())
}

internal class LocalCommandMergerSnapshot(
  val documentReferences: DocumentReference?,
  val actions: UndoRedoListSnapshot<UndoableAction>,
  val lastGroupId: Reference<Any>?,
  val transparent: Boolean,
  @get:Command val commandName: String?,
  val stateBefore: EditorAndState?,
  val stateAfter: EditorAndState?,
  val undoConfirmationPolicy: UndoConfirmationPolicy,
) {
  companion object {
    fun empty() = LocalCommandMergerSnapshot(
      null,
      UndoRedoList<UndoableAction>().snapshot(),
      null,
      false,
      null,
      null,
      null,
      UndoConfirmationPolicy.DEFAULT,
    )
  }
}

internal class LocalUndoRedoSnapshot(
  val localCommandMergerSnapshot: LocalCommandMergerSnapshot,
  val undoStackSnapshot: UndoRedoListSnapshot<UndoableGroup>,
  val redoStackSnapshot: UndoRedoListSnapshot<UndoableGroup>,
)
