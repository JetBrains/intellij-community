// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;

public class FocusBasedCurrentEditorProvider implements CurrentEditorProvider {
  @Override
  public FileEditor getCurrentEditor() {
    DataManager dataManager = DataManager.getInstanceIfCreated();
    if (dataManager == null) return null;
    @SuppressWarnings("deprecation") DataContext context = dataManager.getDataContext();
    return PlatformDataKeys.FILE_EDITOR.getData(context);
  }
}