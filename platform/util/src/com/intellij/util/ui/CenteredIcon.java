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

package com.intellij.util.ui;

import javax.swing.*;
import java.awt.*;

public class CenteredIcon implements Icon {
  private final Icon myIcon;

  private final int myWidth;
  private final int myHight;

  private final boolean myCenteredInComponent;

  public CenteredIcon(Icon icon) {
    this(icon, icon.getIconWidth(), icon.getIconHeight(), true);
  }

  public CenteredIcon(Icon icon, int width, int height) {
    this(icon, width, height, true);
  }

  public CenteredIcon(Icon icon, int width, int height, boolean centeredInComponent) {
    myIcon = icon;
    myWidth = width;
    myHight = height;
    myCenteredInComponent = centeredInComponent;
  }

  public void paintIcon(Component c, Graphics g, int x, int y) {
    int offsetX;
    int offsetY;

    if (myCenteredInComponent) {
      final Dimension size = c.getSize();
      offsetX = size.width / 2 - myIcon.getIconWidth() / 2;
      offsetY = size.height / 2 - myIcon.getIconHeight() / 2;
    }
    else {
      offsetX = (myWidth - myIcon.getIconWidth()) / 2;
      offsetY = (myHight - myIcon.getIconHeight()) / 2;
    }

    myIcon.paintIcon(c, g, x + offsetX, y + offsetY);
  }

  public int getIconWidth() {
    return myWidth;
  }

  public int getIconHeight() {
    return myHight;
  }
}
