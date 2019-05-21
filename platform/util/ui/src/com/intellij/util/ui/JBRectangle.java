// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class JBRectangle extends Rectangle {
  public JBRectangle() {
  }

  public JBRectangle(int x, int y, int width, int height) {
    super(JBUI.scale(x), JBUI.scale(y), JBUI.scale(width), JBUI.scale(height));
  }

  public JBRectangle(Rectangle r) {
    if (r instanceof JBRectangle) {
      x = r.x;
      y = r.y;
      width = r.width;
      height = r.height;
    } else {
      x = JBUI.scale(r.x);
      y = JBUI.scale(r.y);
      width = JBUI.scale(r.width);
      height = JBUI.scale(r.height);
    }
  }

  public void clear() {
    x = y = width = height = 0;
  }
}
