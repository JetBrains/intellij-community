// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public final class FocusBasedCurrentEditorProvider implements CurrentEditorProvider {
  @Override
  public FileEditor getCurrentEditor(@Nullable Project project) {
    return getCurrentEditorEx();
  }

  @ApiStatus.Internal
  public static FileEditor getCurrentEditorEx() {
    DataManager dataManager = DataManager.getInstanceIfCreated();
    if (dataManager == null) return null;
    @SuppressWarnings("deprecation") DataContext context = dataManager.getDataContext();
    return PlatformCoreDataKeys.FILE_EDITOR.getData(context);
  }
}