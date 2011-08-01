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
package com.intellij.ui.plaf.gtk;

import javax.swing.*;
import javax.swing.plaf.MenuItemUI;
import javax.swing.plaf.synth.SynthContext;
import java.awt.*;
import java.lang.reflect.Method;

public class IconWrapper implements Icon {
  private final Icon myIcon;
  private final MenuItemUI myOriginalUI;

  public IconWrapper(final Icon icon, final MenuItemUI originalUI) {
    myIcon = icon;
    myOriginalUI = originalUI;
  }

  @Override
  public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
    try {
      final Method paintIcon = myIcon.getClass().getMethod("paintIcon", SynthContext.class, Graphics.class,
                                                           int.class, int.class, int.class, int.class);
      paintIcon.setAccessible(true);
      paintIcon.invoke(myIcon, GtkPaintingUtil.getSynthContext(myOriginalUI, (JComponent)c), g, x, y, getIconWidth(), getIconHeight());
      return;
    }
    catch (Exception ignore) { }
    myIcon.paintIcon(c, g, x, y);
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
