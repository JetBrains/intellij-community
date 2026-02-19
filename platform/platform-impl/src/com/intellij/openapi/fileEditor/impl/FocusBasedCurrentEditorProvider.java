// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.impl.Utils;
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
      return getCurrentEditorEx0();
    } catch (Throwable ex) {
      LOG.error("Failed to retrieve focused editor", ex);
      return null;
    }
  }

  private static @Nullable FileEditor getCurrentEditorEx0() {
    DataManager dataManager = DataManager.getInstanceIfCreated();
    if (dataManager != null) {
      return getFocusedEditor(dataManager.getDataContext());
    }
    return null;
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
    private final @NotNull Supplier<? extends @Nullable Editor> focusedEditor;

    public TestProvider(@NotNull Supplier<? extends @Nullable Editor> focusedEditor) {
      this.focusedEditor = focusedEditor;
    }

    @Override
    public @Nullable FileEditor getCurrentEditor(@Nullable Project project) {
      Editor editor = focusedEditor.get();
      if (editor != null) {
        DataContext dataContext = Utils.createAsyncDataContext(editor.getContentComponent());
        return getFocusedEditor(dataContext);
      }
      return null;
    }
  }
}
