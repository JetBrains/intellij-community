// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @deprecated Please use {@link EditorComposite} directly
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2023.1")
public class EditorWithProviderComposite extends EditorComposite {
  protected EditorWithProviderComposite(@NotNull VirtualFile file,
                                        @NotNull List<FileEditorWithProvider> editorWithProviders,
                                        @NotNull FileEditorManagerEx fileEditorManager) {
    super(file, editorWithProviders, fileEditorManager);
  }
}
