
/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ui.plaf.beg;

import com.intellij.util.ui.UIUtil;

import javax.swing.border.AbstractBorder;
import javax.swing.plaf.UIResource;
import java.awt.*;

public class BegPopupMenuBorder extends AbstractBorder implements UIResource {
  protected static Insets borderInsets = new Insets(3, 2, 2, 2);
  protected static Color color1 = new Color(214, 211, 206);
  protected static Color color2 = Color.white;
  protected static Color color3 = new Color(132, 130, 132);
  protected static Color color4 = new Color(66, 65, 66);

  public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
    g.translate(x, y);

    g.setColor(color1);
    UIUtil.drawLine(g, 0, 0, w - 2, 0);
    UIUtil.drawLine(g, 0, 0, 0, h - 2);
    g.setColor(color2);
    UIUtil.drawLine(g, 1, 1, w - 3, 1);
    UIUtil.drawLine(g, 1, 1, 1, h - 3);
    g.setColor(color3);
    UIUtil.drawLine(g, 1, h - 2, w - 2, h - 2);
    UIUtil.drawLine(g, w - 2, 1, w - 2, h - 2);
    g.setColor(color4);
    UIUtil.drawLine(g, 0, h - 1, w - 1, h - 1);
    UIUtil.drawLine(g, w - 1, 0, w - 1, h - 1);
    g.translate(-x, -y);
  }

  public Insets getBorderInsets(Component c) {
    return borderInsets;
  }
}
