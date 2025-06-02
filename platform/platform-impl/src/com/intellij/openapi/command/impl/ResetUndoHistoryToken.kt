// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.openapi.command.undo.DocumentReference
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Experimental
@ApiStatus.Internal
class ResetUndoHistoryToken internal constructor(
  private val undoManager: UndoManagerImpl,
  private val reference: DocumentReference,
  private var snapshot: LocalUndoRedoSnapshot?,
) {
  fun resetHistory(): Boolean {
    val snapshot = snapshot ?: return false
    return undoManager.resetLocalHistory(reference, snapshot)
  }

  fun refresh() {
    snapshot = undoManager.getUndoRedoSnapshotForDocument(reference)
  }
}
