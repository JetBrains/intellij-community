// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;


import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.GlobalUndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;


@ApiStatus.Experimental
final class MockGlobalUndoableAction extends GlobalUndoableAction {

  MockGlobalUndoableAction(@NotNull Collection<? extends DocumentReference> docRefs) {
    super(docRefs.toArray(DocumentReference.EMPTY_ARRAY));
  }

  @Override
  public void undo() throws UnexpectedUndoException {
  }

  @Override
  public void redo() throws UnexpectedUndoException {
  }

  private static final class MockDocRef implements DocumentReference {
    @Override
    public @Nullable Document getDocument() {
      return null;
    }

    @Override
    public @Nullable VirtualFile getFile() {
      return null;
    }
  }
}
