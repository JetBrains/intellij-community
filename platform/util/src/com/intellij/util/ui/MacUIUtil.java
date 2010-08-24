/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.GeneralPath;

/**
 * User: spLeaner
 */
public class MacUIUtil {

  private MacUIUtil() {
  }

  public static Color getFocusRingColor() {
    final Object o = UIManager.get("Focus.color");
    if (o instanceof Color) {
      return (Color) o;
    }

    return new Color(64, 113, 167);
  }

  public static void paintComboboxFocusRing(@NotNull final Graphics2D g2d, @NotNull final Rectangle bounds) {
    final Color color = getFocusRingColor();
    final Color[] colors = new Color[] {
      new Color(color.getRed(), color.getGreen(), color.getBlue(), 180),
      new Color(color.getRed(), color.getGreen(), color.getBlue(), 130),
      new Color(color.getRed(), color.getGreen(), color.getBlue(), 80),
      new Color(color.getRed(), color.getGreen(), color.getBlue(), 80)
    };

    final Object oldAntialiasingValue = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    final Object oldStrokeControlValue = g2d.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL);

    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    final boolean useQuartz = true; // TODO:
    g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, useQuartz ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

    //g2d.setStroke(new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

    final GeneralPath path1 = new GeneralPath();
    path1.moveTo(2, 4);
    path1.quadTo(2, 2, 4, 2);
    path1.lineTo(bounds.width - 7, 2);
    path1.quadTo(bounds.width - 5, 3, bounds.width - 4, 5);
    path1.lineTo(bounds.width - 4, bounds.height - 7);
    path1.quadTo(bounds.width - 5, bounds.height - 5, bounds.width - 7, bounds.height - 4);
    path1.lineTo(4, bounds.height - 4);
    path1.quadTo(2, bounds.height - 4, 2, bounds.height - 6);
    path1.closePath();

    g2d.setColor(colors[0]);
    g2d.draw(path1);

    final GeneralPath path2 = new GeneralPath();
    path2.moveTo(1, 5);
    path2.quadTo(1, 1, 5, 1);
    path2.lineTo(bounds.width - 8, 1);
    path2.quadTo(bounds.width - 4, 2, bounds.width - 3, 6);
    path2.lineTo(bounds.width - 3, bounds.height - 7);
    path2.quadTo(bounds.width - 4, bounds.height - 4, bounds.width - 8, bounds.height - 3);
    path2.lineTo(4, bounds.height - 3);
    path2.quadTo(1, bounds.height - 3, 1, bounds.height - 6);
    path2.closePath();

    g2d.setColor(colors[1]);
    g2d.draw(path2);

    final GeneralPath path3 = new GeneralPath();
    path3.moveTo(0, 4);
    path3.quadTo(0, 0, 7, 0);
    path3.lineTo(bounds.width - 9, 0);
    path3.quadTo(bounds.width - 2, 1, bounds.width - 2, 7);
    path3.lineTo(bounds.width - 2, bounds.height - 8);
    path3.quadTo(bounds.width - 3, bounds.height - 1, bounds.width - 12, bounds.height - 2);
    path3.lineTo(7, bounds.height - 2);
    path3.quadTo(0, bounds.height - 1, 0, bounds.height - 7);
    path3.closePath();

    g2d.setColor(colors[2]);
    g2d.draw(path3);

    // restore rendering hints
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasingValue);
    g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, oldStrokeControlValue);
  }

  public static void drawComboboxFocusRing(@NotNull final JComboBox combobox, @NotNull final Graphics g) {
    if (SystemInfo.isMac && combobox.isEnabled() && combobox.isEditable() && UIUtil.isUnderAquaLookAndFeel()) {
      final Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      if (focusOwner != null) {
        final Container ancestor = SwingUtilities.getAncestorOfClass(JComboBox.class, focusOwner);
        if (ancestor == combobox) {
          paintComboboxFocusRing((Graphics2D) g, combobox.getBounds());
        }
      }
    }
  }
}
