// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.editor.ClientEditorManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.util.ui.UIUtil;

import java.util.Optional;

final class ClientCurrentEditorProvider implements CurrentEditorProvider {
  @Override
  public FileEditor getCurrentEditor() {
    Optional<Editor> focusedEditor = ClientEditorManager.getCurrentInstance().editors()
      .filter(e -> UIUtil.hasFocus(e.getComponent()))
      .findFirst();
    if (focusedEditor.isEmpty()) {
      return null;
    }
    return TextEditorProvider.getInstance().getTextEditor(focusedEditor.get());
  }
}
