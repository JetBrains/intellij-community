// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface EditorTabTitleProvider {
  ExtensionPointName<EditorTabTitleProvider> EP_NAME = ExtensionPointName.create("com.intellij.editorTabTitleProvider");

  @Nullable
  String getEditorTabTitle(@NotNull Project project, @NotNull VirtualFile file);

  @Nullable
  default String getEditorTabTooltipText(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    return null;
  }
}
