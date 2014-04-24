/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
