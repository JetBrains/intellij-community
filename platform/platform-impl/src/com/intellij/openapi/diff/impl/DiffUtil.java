// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl;

import com.intellij.openapi.diff.DiffViewer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.util.ImageLoader;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class DiffUtil {
  private DiffUtil() {
  }

  @Deprecated
  public static void initDiffFrame(Project project,
                                   @NotNull FrameWrapper frameWrapper,
                                   @NotNull final DiffViewer diffPanel,
                                   final JComponent mainComponent) {
    frameWrapper.setComponent(mainComponent);
    frameWrapper.setProject(project);
    frameWrapper.setImage(ImageLoader.loadFromResource("/diff/Diff.png"));
    frameWrapper.setPreferredFocusedComponent(diffPanel.getPreferredFocusedComponent());
    frameWrapper.closeOnEsc();
  }

  public static boolean isDiffEditor(@NotNull Editor editor) {
    return editor.getEditorKind() == (EditorKind.DIFF);
  }
}
