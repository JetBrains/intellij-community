// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Experimental
@ApiStatus.Internal
public final class ResetOriginatorAction implements UndoableAction {

  public static final UndoableAction INSTANCE = new ResetOriginatorAction();

  @Override
  public void undo() throws UnexpectedUndoException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void redo() throws UnexpectedUndoException {
    throw new UnsupportedOperationException();
  }

  @Override
  public DocumentReference @Nullable [] getAffectedDocuments() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isGlobal() {
    throw new UnsupportedOperationException();
  }
}
