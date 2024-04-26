// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.editor.ClientEditorManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

final class ClientCurrentEditorProvider implements CurrentEditorProvider {
  @Override
  public @Nullable FileEditor getCurrentEditor(@Nullable Project project) {
    if (project == null) {
      // fallback to search by focus
      Optional<Editor> focusedEditor = ClientEditorManager.getCurrentInstance().editors()
        .filter(e -> UIUtil.hasFocus(e.getContentComponent()))
        .findFirst();
      return focusedEditor.map(editor -> TextEditorProvider.getInstance().getTextEditor(editor)).orElse(null);
    }
    else {
      return FileEditorManager.getInstance(project).getSelectedEditor();
    }
  }
}
