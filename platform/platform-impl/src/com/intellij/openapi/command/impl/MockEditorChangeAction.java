// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;


import com.intellij.openapi.command.undo.AdjustableUndoableAction;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.MutableActionChangeRange;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;


@ApiStatus.Experimental
final class MockEditorChangeAction extends BasicUndoableAction implements AdjustableUndoableAction {

  MockEditorChangeAction(@NotNull DocumentReference docRef) {
    super(docRef);
  }

  @Override
  public void undo() {
  }

  @Override
  public void redo() {
  }

  @Override
  public @NotNull List<MutableActionChangeRange> getChangeRanges(@NotNull DocumentReference reference) {
    return Collections.emptyList();
  }
}
