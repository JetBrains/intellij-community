// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.fileEditor.impl.EditorTabPresentationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

import static com.intellij.openapi.util.text.StringUtil.escapeMnemonics;
import static com.intellij.openapi.util.text.StringUtil.firstLast;

/**
 * @author gregsh
 */
public class VfsPresentationUtil {

  @NotNull
  public static String getPresentableNameForAction(@NotNull Project project, @NotNull VirtualFile file) {
    return escapeMnemonics(firstLast(getPresentableNameForUI(project, file), 20));
  }

  @NotNull
  public static String getPresentableNameForUI(@NotNull Project project, @NotNull VirtualFile file) {
    return EditorTabPresentationUtil.getEditorTabTitle(project, file, null);
  }

  @NotNull
  public static String getUniquePresentableNameForUI(@NotNull Project project, @NotNull VirtualFile file) {
    return EditorTabPresentationUtil.getUniqueEditorTabTitle(project, file, null);
  }

  @Nullable
  public static Color getFileTabBackgroundColor(@NotNull Project project, @NotNull VirtualFile file) {
    return EditorTabPresentationUtil.getEditorTabBackgroundColor(project, file, null);
  }

  @Nullable
  public static Color getFileBackgroundColor(@NotNull Project project, @NotNull VirtualFile file) {
    return EditorTabPresentationUtil.getFileBackgroundColor(project, file);
  }
}
