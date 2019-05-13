/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.util.ui;


import javax.swing.*;
import java.awt.*;

public class WatermarkIcon implements Icon {

  private final Icon myIcon;
  private final float myAlpha;

  public WatermarkIcon(Icon icon, float alpha) {
    myIcon = icon;
    myAlpha = alpha;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    Graphics graphics = g.create();
    ((Graphics2D)graphics).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, myAlpha));
    myIcon.paintIcon(c, graphics, x, y);
  }

  @Override
  public int getIconWidth() {
    return myIcon.getIconWidth();
  }

  @Override
  public int getIconHeight() {
    return myIcon.getIconHeight();
  }

}
