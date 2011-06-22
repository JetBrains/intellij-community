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

import com.intellij.util.ui.UIUtil;
import sun.swing.SwingUtilities2;
import sun.swing.plaf.synth.SynthUI;

import javax.swing.*;
import javax.swing.plaf.basic.BasicMenuItemUI;
import javax.swing.plaf.synth.ColorType;
import javax.swing.plaf.synth.SynthContext;
import java.awt.*;

public class GtkPaintingUtil {
  private GtkPaintingUtil() { }

  public static Color getForeground(final BasicMenuItemUI ui, final JMenuItem menuItem) {
    final SynthContext context = ((SynthUI)ui).getContext(menuItem);
    return context.getStyle().getColor(context, ColorType.TEXT_FOREGROUND);
  }

  public static void paintDisabledText(final BasicMenuItemUI originalUI,
                                       final Graphics g,
                                       final JMenuItem menuItem,
                                       final Rectangle textRect,
                                       final String text) {
    final FontMetrics fm = SwingUtilities2.getFontMetrics(menuItem, g);
    final int index = menuItem.getDisplayedMnemonicIndex();

    final Color fg = getForeground(originalUI, menuItem);
    final Color shadow = UIUtil.shade(menuItem.getBackground(), 1.24, 0.5);

    g.setColor(shadow);
    SwingUtilities2.drawStringUnderlineCharAt(menuItem, g, text, index, textRect.x + 1, textRect.y + fm.getAscent() + 1);
    g.setColor(fg);
    SwingUtilities2.drawStringUnderlineCharAt(menuItem, g, text, index, textRect.x, textRect.y + fm.getAscent());
  }
}
