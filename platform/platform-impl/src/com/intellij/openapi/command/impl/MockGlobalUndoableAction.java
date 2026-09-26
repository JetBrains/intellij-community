// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;


import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.GlobalUndoableAction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;


@ApiStatus.Experimental
final class MockGlobalUndoableAction extends GlobalUndoableAction {

  MockGlobalUndoableAction(@NotNull Collection<DocumentReference> docRefs) {
    super(docRefs.toArray(DocumentReference.EMPTY_ARRAY));
  }

  @Override
  public void undo() {
  }

  @Override
  public void redo() {
  }
}
