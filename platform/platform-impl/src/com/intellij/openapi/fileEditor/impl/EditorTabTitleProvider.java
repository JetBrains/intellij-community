// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <p>Provides custom name/tooltip for editor tab instead of filename/path.</p>
 *
 * <p>Implement {@link com.intellij.openapi.project.DumbAware} to be active during indexing.</p>
 *
 * @author yole
 */
public interface EditorTabTitleProvider {
  ExtensionPointName<EditorTabTitleProvider> EP_NAME = ExtensionPointName.create("com.intellij.editorTabTitleProvider");

  @NlsContexts.TabTitle @Nullable String getEditorTabTitle(@NotNull Project project, @NotNull VirtualFile file);

  default @NlsContexts.TabTitle @Nullable String getEditorTabTitle(@NotNull Project project, @NotNull VirtualFile file, @SuppressWarnings("unused") @Nullable EditorWindow ew) {
    return getEditorTabTitle(project, file);
  }

  default @NlsContexts.Tooltip @Nullable String getEditorTabTooltipText(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    return null;
  }
}
