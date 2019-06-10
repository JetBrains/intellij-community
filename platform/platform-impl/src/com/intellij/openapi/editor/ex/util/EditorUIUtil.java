// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex.util;

import com.intellij.ide.ui.AntialiasingType;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;

import java.awt.*;

/**
 * @author Denis Fokin
 */
public class EditorUIUtil {

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
    if (SystemInfo.isMac) {
      MacUIUtil.hideCursor();
    }
    else if (editor instanceof EditorImpl) {
      ((EditorImpl)editor).hideCursor();
    }
  }
}
