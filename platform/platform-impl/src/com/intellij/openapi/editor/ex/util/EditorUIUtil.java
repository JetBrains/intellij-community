// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex.util;

import com.intellij.ide.ui.AntialiasingType;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public final class EditorUIUtil {

  /* This method has to be used for setting up antialiasing and rendering hints in
 * editors only.
 */
  public static void setupAntialiasing(final Graphics g) {

    Graphics2D g2d = (Graphics2D)g;

    int lcdContrastValue = UIUtil.getLcdContrastValue();

    g2d.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, lcdContrastValue);
    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(true));

    UISettings.setupFractionalMetrics(g2d);
  }

  public static void hideCursorInEditor(Editor editor) {
    if (editor instanceof EditorImpl) {
      ((EditorImpl)editor).hideCursor();
    }
  }

  public static Icon scaleIcon(Icon icon, @NotNull EditorImpl editor) {
    float scale = getEditorScaleFactor(editor);
    return scale == 1 ? icon : IconUtil.scale(icon, editor.getComponent(), scale);
  }

  public static int scaleWidth(int width, EditorImpl editor) {
    return (int)(getEditorScaleFactor(editor) * width);
  }

  private static float getEditorScaleFactor(@NotNull EditorImpl editor) {
    if (Registry.is("editor.scale.gutter.icons")) {
      float scale = editor.getScale();
      if (Math.abs(1f - scale) > 0.10f) {
        return scale;
      }
    }
    return 1f;
  }

  private EditorUIUtil() {
  }
}
