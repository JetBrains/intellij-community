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

import com.intellij.Patches;
import com.intellij.util.ui.UIUtil;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.plaf.MenuItemUI;
import javax.swing.plaf.basic.BasicMenuItemUI;
import javax.swing.plaf.synth.ColorType;
import javax.swing.plaf.synth.SynthContext;
import java.awt.*;
import java.lang.reflect.Method;

// todo[r.sh] get rid of SynthUI reflection after migration to JDK 7
public class GtkPaintingUtil {
  private static final String V6_SYNTH_UI_CLASS = "sun.swing.plaf.synth.SynthUI";
  private static final String V7_SYNTH_UI_CLASS = "javax.swing.plaf.synth.SynthUI";

  private GtkPaintingUtil() { }

  public static Color getForeground(final BasicMenuItemUI ui, final JMenuItem menuItem) {
    final SynthContext context = getSynthContext(ui, menuItem);
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

  public static boolean isSynthUI(final MenuItemUI ui) {
    Class<?> aClass = ui.getClass();

    while (aClass != null && aClass.getSimpleName().contains("Synth")) {
      final Class<?>[] interfaces = aClass.getInterfaces();
      for (int i = 0, length = interfaces.length; i < length; i++) {
        final Class<?> anInterface = interfaces[i];
        if (V6_SYNTH_UI_CLASS.equals(anInterface.getName()) || V7_SYNTH_UI_CLASS.equals(anInterface.getName())) {
          return true;
        }
      }
      aClass = aClass.getSuperclass();
    }

    return false;
  }

  public static SynthContext getSynthContext(final MenuItemUI ui, final JComponent item) {
    assert Patches.USE_REFLECTION_TO_ACCESS_JDK7;
    try {
      final Method getContext = ui.getClass().getMethod("getContext", JComponent.class);
      getContext.setAccessible(true);
      return (SynthContext)getContext.invoke(ui, item);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
