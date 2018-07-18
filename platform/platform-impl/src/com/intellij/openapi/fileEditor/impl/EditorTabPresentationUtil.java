// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

public class EditorTabPresentationUtil {
  @NotNull
  public static String getEditorTabTitle(@NotNull Project project, @NotNull VirtualFile file, @Nullable EditorWindow editorWindow) {
    List<EditorTabTitleProvider> providers = DumbService.getInstance(project).filterByDumbAwareness(
      Extensions.getExtensions(EditorTabTitleProvider.EP_NAME));
    for (EditorTabTitleProvider provider : providers) {
      String result = provider.getEditorTabTitle(project, file, editorWindow);
      if (result != null) {
        return result;
      }
    }

    return file.getPresentableName();
  }

  @NotNull
  public static String getUniqueEditorTabTitle(@NotNull Project project, @NotNull VirtualFile file, @Nullable EditorWindow editorWindow) {
    String name = getEditorTabTitle(project, file, editorWindow);
    if (name.equals(file.getPresentableName())) {
      return UniqueVFilePathBuilder.getInstance().getUniqueVirtualFilePath(project, file);
    }
    return name;
  }

  @Nullable
  public static Color getEditorTabBackgroundColor(@NotNull Project project, @NotNull VirtualFile file,
                                                  @Nullable EditorWindow editorWindow) {
    List<EditorTabColorProvider> providers = DumbService.getInstance(project).filterByDumbAwareness(
      Extensions.getExtensions(EditorTabColorProvider.EP_NAME));
    for (EditorTabColorProvider provider : providers) {
      Color result = provider.getEditorTabColor(project, file, editorWindow);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @Nullable
  public static Color getFileBackgroundColor(@NotNull Project project, @NotNull VirtualFile file) {
    List<EditorTabColorProvider> providers = DumbService.getInstance(project).filterByDumbAwareness(
      Extensions.getExtensions(EditorTabColorProvider.EP_NAME));
    for (EditorTabColorProvider provider : providers) {
      Color result = provider.getProjectViewColor(project, file);
      if (result != null) {
        return result;
      }
    }
    return null;
  }
}
