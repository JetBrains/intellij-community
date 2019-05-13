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

/*
 * @author max
 */
package com.intellij.ui;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author max
 */
public class EngravedLabel extends JLabel {
  private Color myShadowColor = EngravedTextGraphics.SHADOW_COLOR;

  public EngravedLabel(String text) {
    super(text);
    setOpaque(false);
  }

  public EngravedLabel() {
    this("");
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    if (!UIUtil.isUnderDarcula()) {
      graphics = new EngravedTextGraphics((Graphics2D)graphics, 0, 1, getShadowColor());
    }
    super.paintComponent(graphics);
  }

  public Color getShadowColor() {
    return myShadowColor == null ? EngravedTextGraphics.SHADOW_COLOR : myShadowColor;
  }

  public void setShadowColor(Color shadowColor) {
    myShadowColor = shadowColor;
  }
}
