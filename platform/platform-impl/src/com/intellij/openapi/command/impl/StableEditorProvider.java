// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;


final class StableEditorProvider implements CurrentEditorProvider {
  private final @Nullable FileEditor editor;

  StableEditorProvider(@Nullable FileEditor editor) {
    this.editor = editor;
  }

  @SuppressWarnings("UsagesOfObsoleteApi")
  @Override
  public @Nullable FileEditor getCurrentEditor() {
    return editor;
  }

  @Override
  public @Nullable FileEditor getCurrentEditor(@Nullable Project project) {
    return editor;
  }
}
