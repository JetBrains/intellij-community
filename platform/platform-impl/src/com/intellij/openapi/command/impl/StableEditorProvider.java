// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


final class StableEditorProvider implements CurrentEditorProvider {
  private final @NotNull CurrentEditorProvider provider;
  private @Nullable FileEditor editor;
  private boolean isInitialized;

  StableEditorProvider(@NotNull CurrentEditorProvider provider) {
    this.provider = provider;
  }

  @Override
  public @Nullable FileEditor getCurrentEditor(@Nullable Project project) {
    if (isInitialized) {
      return editor;
    }
    editor = ProgressManager.getInstance().computeInNonCancelableSection(
      () -> provider.getCurrentEditor(project)
    );
    isInitialized = true;
    return editor;
  }
}
