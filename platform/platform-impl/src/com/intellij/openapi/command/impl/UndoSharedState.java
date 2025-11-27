// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.AdjustableUndoableAction;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.MutableActionChangeRange;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Supplier;


final class UndoSharedState {
  private final @NotNull SharedAdjustableUndoableActionsHolder adjustableActions;
  private final @NotNull SharedUndoRedoStacksHolder undoStacks;
  private final @NotNull SharedUndoRedoStacksHolder redoStacks;

  UndoSharedState(@NotNull Supplier<Boolean> isPerClientSupported) {
    adjustableActions = new SharedAdjustableUndoableActionsHolder();
    undoStacks = new SharedUndoRedoStacksHolder(adjustableActions, isPerClientSupported, true);
    redoStacks = new SharedUndoRedoStacksHolder(adjustableActions, isPerClientSupported, false);
  }

  @NotNull SharedUndoRedoSnapshot getSharedUndoRedoSnapshot(@NotNull DocumentReference reference) {
    return new SharedUndoRedoSnapshot(
      adjustableActions.getStack(reference).snapshot(),
      undoStacks.getStack(reference).snapshot(),
      redoStacks.getStack(reference).snapshot()
    );
  }

  void resetLocalHistory(@NotNull DocumentReference reference, @NotNull SharedUndoRedoSnapshot snapshot) {
    adjustableActions.getStack(reference).resetTo(snapshot.getActionsHolderSnapshot());
    undoStacks.getStack(reference).resetTo(snapshot.getSharedUndoStack());
    redoStacks.getStack(reference).resetTo(snapshot.getSharedRedoStack());
  }

  void clearDocumentReferences(@NotNull Document document) {
    undoStacks.clearDocumentReferences(document);
    redoStacks.clearDocumentReferences(document);
  }

  void trimSharedStacks(@NotNull DocumentReference docRef) {
    redoStacks.trimStacks(Collections.singleton(docRef));
    undoStacks.trimStacks(Collections.singleton(docRef));
  }

  void trimStacks(@NotNull Collection<? extends DocumentReference> references) {
    undoStacks.trimStacks(references);
    redoStacks.trimStacks(references);
  }

  void addAction(@NotNull UndoableAction action) {
    if (action instanceof AdjustableUndoableAction adjustable) {
      DocumentReference[] affected = action.getAffectedDocuments();
      if (affected == null) {
        return;
      }
      adjustableActions.addAction(adjustable);
      for (DocumentReference reference : affected) {
        for (MutableActionChangeRange changeRange : adjustable.getChangeRanges(reference)) {
          undoStacks.addToStack(reference, changeRange.toImmutable(false));
          redoStacks.addToStack(reference, changeRange.toImmutable(true));
        }
      }
    }
  }

  @NotNull SharedAdjustableUndoableActionsHolder getAdjustableActions() {
    return adjustableActions;
  }

  @NotNull SharedUndoRedoStacksHolder getUndoStacks() {
    return undoStacks;
  }

  @NotNull SharedUndoRedoStacksHolder getRedoStacks() {
    return redoStacks;
  }
}
