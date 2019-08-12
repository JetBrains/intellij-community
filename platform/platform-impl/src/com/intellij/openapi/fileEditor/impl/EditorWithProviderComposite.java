// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Left around for API compatibility. Please use EditorComposite directly whenever possible.
 */
public class EditorWithProviderComposite extends EditorComposite {
  EditorWithProviderComposite(@NotNull VirtualFile file,
                              @NotNull FileEditor[] editors,
                              @NotNull FileEditorProvider[] providers,
                              @NotNull FileEditorManagerEx fileEditorManager) {
    super(file, editors, providers, fileEditorManager);
  }
}
