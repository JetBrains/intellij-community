// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.function.Supplier;

public final class FocusBasedCurrentEditorProvider implements CurrentEditorProvider {
  private static final Logger LOG = Logger.getInstance(FocusBasedCurrentEditorProvider.class);

  @Override
  public @Nullable FileEditor getCurrentEditor(@Nullable Project project) {
    return getCurrentEditorEx();
  }

  @ApiStatus.Internal
  public static @Nullable FileEditor getCurrentEditorEx() {
    try {
      return FocusedFileEditor.getFocusedFileEditor(null);
    } catch (Throwable ex) {
      LOG.error("Failed to retrieve focused editor", ex);
      return null;
    }
  }

  /**
   * Ensures that behavior in tests is the same as in production, see IJPL-215217
   */
  @ApiStatus.Internal
  @TestOnly
  public static final class TestProvider implements CurrentEditorProvider {
    private final @NotNull Supplier<? extends @Nullable Editor> focusedEditor;

    public TestProvider(@NotNull Supplier<? extends @Nullable Editor> focusedEditor) {
      this.focusedEditor = focusedEditor;
    }

    @Override
    public @Nullable FileEditor getCurrentEditor(@Nullable Project project) {
      Editor editor = focusedEditor.get();
      var component = editor == null ? null : editor.getContentComponent();
      return FocusedFileEditor.getFocusedFileEditor(component);
    }
  }
}
