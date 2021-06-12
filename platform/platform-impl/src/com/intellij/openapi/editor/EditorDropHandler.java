// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;


public interface EditorDropHandler {
  boolean canHandleDrop(DataFlavor[] transferFlavors);
  void handleDrop(Transferable t, final Project project, EditorWindow editorWindow);
  default void handleDrop(Transferable t, final Project project, EditorWindow editorWindow, @SuppressWarnings("unused") int dropAction) {
    handleDrop(t, project, editorWindow);
  }
}
