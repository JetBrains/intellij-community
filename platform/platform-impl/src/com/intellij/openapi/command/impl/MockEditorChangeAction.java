// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;


import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.DocumentReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Experimental
final class MockEditorChangeAction extends BasicUndoableAction {

  MockEditorChangeAction(@NotNull DocumentReference docRef) {
    super(docRef);
  }

  @Override
  public void undo() {
  }

  @Override
  public void redo() {
  }
}
