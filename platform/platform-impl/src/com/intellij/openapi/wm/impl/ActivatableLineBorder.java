/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl;

import com.intellij.util.ui.UIUtil;

import javax.swing.border.Border;
import java.awt.*;

public class ActivatableLineBorder implements Border {

  private boolean active = false;

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public Insets getBorderInsets(Component c) {
    return new Insets(1, 1, 1, 1);
  }

  public boolean isBorderOpaque() {
    return false;
  }

  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    final Color lineColor = active ? UIUtil.getBorderActiveColor() : UIUtil.getBorderInactiveColor();
    g.setColor(lineColor);

    UIUtil.drawLine(g, x + 1, y, x + width - 2, y);
    UIUtil.drawLine(g, x + 1, y + height - 1, x + width - 2, y + height - 1);
    UIUtil.drawLine(g, x, y + 1, x, y + height - 2);
    UIUtil.drawLine(g, x + width - 1, y + 1, x + width - 1, y + height - 2);
  }
}
