// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.util.NlsContexts.Command;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;


record PerformedCommand(
  @NotNull CommandId commandId,
  @Nullable @Command String commandName,
  @Nullable Object groupId,
  @NotNull UndoConfirmationPolicy confirmationPolicy,
  @Nullable EditorAndState editorStateBefore,
  @Nullable EditorAndState editorStateAfter,
  @NotNull Collection<UndoableAction> undoableActions,
  @NotNull UndoAffectedDocuments affectedDocuments,
  @NotNull UndoAffectedDocuments additionalAffectedDocuments,
  boolean isTransparent,
  boolean isForcedGlobal,
  boolean isGlobal,
  boolean isValid,
  boolean isForeign
) {

  boolean hasActions() {
    return !undoableActions.isEmpty();
  }

  boolean shouldClearRedoStack() {
    return !isTransparent() && hasActions();
  }

  boolean shouldRecordId() {
    return hasActions() || affectedDocuments.size() > 0 || additionalAffectedDocuments.size() > 0;
  }
}
