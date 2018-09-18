// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;

import java.awt.*;

/**
 * @author max
 */
class FocusBasedCurrentEditorProvider implements CurrentEditorProvider {
  @Override
  public FileEditor getCurrentEditor() {
    // [kirillk] this is a hack, since much of editor-related code was written long before
    // own focus managenent in the platform, so this method should be strictly synchronous
    final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    return PlatformDataKeys.FILE_EDITOR.getData(DataManager.getInstance().getDataContext(owner));
  }
}