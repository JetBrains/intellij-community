// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;


public interface EditorDropHandler {
  boolean canHandleDrop(@NotNull DataFlavor @NotNull [] transferFlavors);
  void handleDrop(@NotNull Transferable t, @Nullable Project project, @Nullable EditorWindow editorWindow);
  default void handleDrop(@NotNull Transferable t, @Nullable Project project, @Nullable EditorWindow editorWindow, @SuppressWarnings("unused") int dropAction) {
    handleDrop(t, project, editorWindow);
  }
}
