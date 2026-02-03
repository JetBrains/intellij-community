// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.DocumentReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;


@ApiStatus.Experimental
final class MockUndoableAction extends BasicUndoableAction {
  private final boolean isGlobal;

  MockUndoableAction(@Nullable Collection<DocumentReference> docRefs, boolean isGlobal) {
    super(docRefs == null ? null : docRefs.toArray(DocumentReference.EMPTY_ARRAY));
    this.isGlobal = isGlobal;
  }

  @Override
  public boolean isGlobal() {
    return isGlobal;
  }

  @Override
  public void undo() {
  }

  @Override
  public void redo() {
  }
}
