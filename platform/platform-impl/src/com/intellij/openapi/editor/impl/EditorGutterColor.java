// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.ui.ExperimentalUI;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public final class EditorGutterColor {
  private EditorGutterColor() {
  }

  public static @NotNull Color getEditorGutterBackgroundColor(@NotNull EditorImpl editor, boolean paintBackground) {
    if (editor.isInDistractionFreeMode() || !paintBackground) {
      return editor.getBackgroundColor();
    }

    if (ExperimentalUI.isNewUI()) {
      Color bg = editor.getColorsScheme().getColor(EditorColors.EDITOR_GUTTER_BACKGROUND);
      return bg == null ? editor.getBackgroundColor() : bg;
    }

    Color color = editor.getColorsScheme().getColor(EditorColors.GUTTER_BACKGROUND);
    return color != null ? color : EditorColors.GUTTER_BACKGROUND.getDefaultColor();
  }
}
