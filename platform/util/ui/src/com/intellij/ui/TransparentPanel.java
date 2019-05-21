// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("UnusedDeclaration")
public class TransparentPanel extends JPanel {
  private float myOpacity;

  public TransparentPanel(float opacity, LayoutManager layout) {
    super(layout);
    if (opacity > 1.0 || opacity < 0.0) {
      throw new IllegalArgumentException("Opacity should be in range [0.0 .. 1.0]");
    }
    myOpacity = opacity;
  }

  public TransparentPanel(float opacity) {
    myOpacity = opacity;
  }

  public TransparentPanel() {
    myOpacity = 0.7f;
  }

  public float getOpacity() {
    return myOpacity;
  }

  public void setOpacity(float opacity) {
    myOpacity = opacity;
  }

  @Override
  public void paint(Graphics g) {
    ((Graphics2D)g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, myOpacity));
    super.paint(g);
  }
}
