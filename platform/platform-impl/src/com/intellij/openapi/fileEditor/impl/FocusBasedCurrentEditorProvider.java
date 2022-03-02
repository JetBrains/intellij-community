// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;

public final class FocusBasedCurrentEditorProvider implements CurrentEditorProvider {
  @Override
  public FileEditor getCurrentEditor() {
    DataManager dataManager = DataManager.getInstanceIfCreated();
    if (dataManager == null) return null;
    @SuppressWarnings("deprecation") DataContext context = dataManager.getDataContext();
    return PlatformCoreDataKeys.FILE_EDITOR.getData(context);
  }
}