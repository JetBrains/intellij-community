// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class FocusBasedCurrentEditorProvider implements CurrentEditorProvider {
  @Override
  public FileEditor getCurrentEditor(@Nullable Project project) {
    return getCurrentEditorEx();
  }

  @ApiStatus.Internal
  public static FileEditor getCurrentEditorEx() {
    DataManager dataManager = DataManager.getInstanceIfCreated();
    if (dataManager == null) return null;
    return getFocusedEditor(dataManager.getDataContext());
  }

  private static @Nullable FileEditor getFocusedEditor(@NotNull DataContext dataContext) {
    return PlatformCoreDataKeys.FILE_EDITOR.getData(dataContext);
  }

  /**
   * Ensures that behavior in tests is the same as in production, see IJPL-215217
   */
  @ApiStatus.Internal
  @TestOnly
  public static final class TestProvider implements CurrentEditorProvider {
    private final @NotNull Editor editor;

    public TestProvider(@NotNull Editor editor) {
      this.editor = editor;
    }

    @Override
    public FileEditor getCurrentEditor(@Nullable Project project) {
      return getFocusedEditor(Utils.createAsyncDataContext(editor.getContentComponent()));
    }
  }
}
