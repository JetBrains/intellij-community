// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.ui.scale.JBUIScale;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class JBRectangle extends Rectangle {
  public JBRectangle() {
  }

  public JBRectangle(int x, int y, int width, int height) {
    super(JBUIScale.scale(x), JBUIScale.scale(y), JBUIScale.scale(width), JBUIScale.scale(height));
  }

  public JBRectangle(Rectangle r) {
    if (r instanceof JBRectangle) {
      x = r.x;
      y = r.y;
      width = r.width;
      height = r.height;
    } else {
      x = JBUIScale.scale(r.x);
      y = JBUIScale.scale(r.y);
      width = JBUIScale.scale(r.width);
      height = JBUIScale.scale(r.height);
    }
  }

  public void clear() {
    x = y = width = height = 0;
  }
}
