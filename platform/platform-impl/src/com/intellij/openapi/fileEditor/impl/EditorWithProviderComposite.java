// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @deprecated Left around for API compatibility. Please use EditorComposite directly whenever possible.
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2023.1")
public class EditorWithProviderComposite extends EditorComposite {
  public EditorWithProviderComposite(@NotNull VirtualFile file,
                                     @NotNull List<FileEditorWithProvider> editorWithProviders,
                                     @NotNull FileEditorManagerEx fileEditorManager) {
    super(file, editorWithProviders, fileEditorManager);
  }
}
