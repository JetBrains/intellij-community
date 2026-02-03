// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


// TODO: remove
@ApiStatus.Experimental
@ApiStatus.Internal
public record UndoRedoStackSize(
  @NotNull DocumentReference docRef,
  int undoSize,
  int redoSize
) {
}
