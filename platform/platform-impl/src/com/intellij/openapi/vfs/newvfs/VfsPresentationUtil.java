// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.fileEditor.impl.EditorTabPresentationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

import static com.intellij.openapi.util.text.StringUtil.escapeMnemonics;
import static com.intellij.openapi.util.text.StringUtil.firstLast;

/**
 * @author gregsh
 */
public final class VfsPresentationUtil {

  public static @NotNull String getPresentableNameForAction(@NotNull Project project, @NotNull VirtualFile file) {
    return escapeMnemonics(firstLast(getPresentableNameForUI(project, file), 20));
  }

  public static @NotNull @NlsContexts.TabTitle String getPresentableNameForUI(@NotNull Project project, @NotNull VirtualFile file) {
    return EditorTabPresentationUtil.getEditorTabTitle(project, file);
  }

  public static @Nullable @NlsContexts.TabTitle String getCustomPresentableNameForUI(@NotNull Project project, @NotNull VirtualFile file) {
    return EditorTabPresentationUtil.getCustomEditorTabTitle(project, file);
  }

  public static @NotNull String getUniquePresentableNameForUI(@NotNull Project project, @NotNull VirtualFile file) {
    return EditorTabPresentationUtil.getUniqueEditorTabTitle(project, file);
  }

  public static @Nullable Color getFileTabBackgroundColor(@NotNull Project project, @NotNull VirtualFile file) {
    return EditorTabPresentationUtil.getEditorTabBackgroundColor(project, file);
  }

  public static @Nullable Color getFileBackgroundColor(@NotNull Project project, @NotNull VirtualFile file) {
    return EditorTabPresentationUtil.getFileBackgroundColor(project, file);
  }
}
