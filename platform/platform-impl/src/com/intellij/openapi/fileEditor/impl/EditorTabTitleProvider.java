// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides custom name/tooltip for editor tab instead of filename/path.
 * <p>
 * Implement {@link com.intellij.openapi.project.DumbAware} to be active during indexing.
 *
 * @author yole
 */
public interface EditorTabTitleProvider {
  ExtensionPointName<EditorTabTitleProvider> EP_NAME = ExtensionPointName.create("com.intellij.editorTabTitleProvider");

  @Nullable
  String getEditorTabTitle(@NotNull Project project, @NotNull VirtualFile file);

  @Nullable
  default String getEditorTabTitle(@NotNull Project project, @NotNull VirtualFile file, @Nullable EditorWindow editorWindow) {
    return getEditorTabTitle(project, file);
  }

  @Nullable
  default String getEditorTabTooltipText(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    return null;
  }
}
