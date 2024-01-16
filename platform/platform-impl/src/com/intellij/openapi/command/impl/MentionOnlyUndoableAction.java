// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoableAction;
import org.jetbrains.annotations.NotNull;

/**
 * Used to make Undo/Redo action available for some Document, even if it was not modified.
 * Undo action can be available even if this Document is ReadOnly.
 */
final class MentionOnlyUndoableAction implements UndoableAction {
  private long myPerformedTimestamp = -1L;
  private final DocumentReference[] myRefs;

  MentionOnlyUndoableAction(DocumentReference @NotNull [] refs) {
    myRefs = refs;
  }

  @Override
  public void undo() {
  }

  @Override
  public void redo() {
  }

  @Override
  public DocumentReference[] getAffectedDocuments() {
    return myRefs;
  }

  @Override
  public boolean isGlobal() {
    return false;
  }

  @Override
  public long getPerformedNanoTime() {
    return myPerformedTimestamp;
  }

  @Override
  public void setPerformedNanoTime(long l) {
    myPerformedTimestamp = l;
  }
}
