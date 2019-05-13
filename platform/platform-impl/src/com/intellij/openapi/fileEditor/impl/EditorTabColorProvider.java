// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author spleaner
 */
public interface EditorTabColorProvider {
  ExtensionPointName<EditorTabColorProvider> EP_NAME = ExtensionPointName.create("com.intellij.editorTabColorProvider");

  @Nullable
  Color getEditorTabColor(@NotNull Project project, @NotNull VirtualFile file);

  @Nullable
  default Color getEditorTabColor(@NotNull Project project, @NotNull VirtualFile file, @Nullable EditorWindow editorWindow) {
    return getEditorTabColor(project, file);
  }

  @Nullable
  default Color getProjectViewColor(@NotNull Project project, @NotNull VirtualFile file) {
    return null;
  }
}
