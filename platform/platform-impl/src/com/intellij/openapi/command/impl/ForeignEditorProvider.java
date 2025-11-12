// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Experimental
@ApiStatus.Internal
public record ForeignEditorProvider(
  @Nullable FileEditor editor,
  @Nullable DocumentReference originator
) implements CurrentEditorProvider {

  @Override
  public @Nullable FileEditor getCurrentEditor() {
    return editor;
  }

  @Override
  public @Nullable FileEditor getCurrentEditor(@Nullable Project project) {
    return editor;
  }
}
