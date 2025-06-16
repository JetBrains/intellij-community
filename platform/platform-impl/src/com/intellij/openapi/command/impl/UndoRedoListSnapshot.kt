// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.command.undo.AdjustableUndoableAction
import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.ImmutableActionChangeRange
import com.intellij.openapi.command.undo.UndoableAction
import com.intellij.openapi.util.NlsContexts.Command
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import java.lang.ref.Reference


internal class UndoRedoListSnapshot<T>(val snapshot: PersistentList<T>) {
  fun toList(): UndoRedoList<T> = UndoRedoList(snapshot.builder())
}

internal class UndoRedoSetSnapshot<T>(val snapshot: PersistentSet<T>)

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
  val clientSnapshots: Map<ClientId, PerClientLocalUndoRedoSnapshot>,
  val sharedUndoStack: UndoRedoListSnapshot<ImmutableActionChangeRange>,
  val sharedRedoStack: UndoRedoListSnapshot<ImmutableActionChangeRange>,
)

internal class PerClientLocalUndoRedoSnapshot(
  val localCommandMergerSnapshot: LocalCommandMergerSnapshot,
  val undoStackSnapshot: UndoRedoListSnapshot<UndoableGroup>,
  val redoStackSnapshot: UndoRedoListSnapshot<UndoableGroup>,
  val actionsHolderSnapshot: UndoRedoSetSnapshot<AdjustableUndoableAction>,
) {
  companion object {
    @JvmStatic
    fun empty(): PerClientLocalUndoRedoSnapshot = PerClientLocalUndoRedoSnapshot(
      LocalCommandMergerSnapshot.empty(),
      UndoRedoList<UndoableGroup>().snapshot(),
      UndoRedoList<UndoableGroup>().snapshot(),
      UndoRedoSet<AdjustableUndoableAction>().snapshot(),
    )
  }
}
