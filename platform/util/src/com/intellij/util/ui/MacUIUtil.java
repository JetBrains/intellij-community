/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.util.ui;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;
import java.awt.geom.GeneralPath;
import java.lang.reflect.Field;

public class MacUIUtil {

  public static final boolean USE_QUARTZ = "true".equals(System.getProperty("apple.awt.graphics.UseQuartz"));
  public static final String MAC_FILL_BORDER = "MAC_FILL_BORDER";
  public static final int MAC_COMBO_BORDER_V_OFFSET = SystemInfo.isMacOSLion ? 1 : 0;
  private static Cursor INVERTED_TEXT_CURSOR;

  private MacUIUtil() {}

  public static void hideCursor() {
    if (SystemInfo.isMac && Registry.is("ide.mac.hide.cursor.when.typing")) {
      Foundation.invoke("NSCursor", "setHiddenUntilMouseMoves:", true);
    }
  }

  public static void drawToolbarDecoratorBackground(Graphics g2, int width, int height) {
    final Graphics2D g = (Graphics2D)g2;
    final int h1 = height / 2;
    g.setPaint(UIUtil.getGradientPaint(0,0, Gray._247, 0, h1, Gray._240));
    g.fillRect(0, 0, width, h1);
    g.setPaint(UIUtil.getGradientPaint(0,h1, Gray._229, 0, height, Gray._234));
    g.fillRect(0, h1, width, height);
  }

  public static Color getFocusRingColor() {
    final Object o = UIManager.get("Focus.color");
    if (o instanceof Color) {
      return (Color)o;
    }

    //noinspection UseJBColor
    return new Color(64, 113, 167);
  }

  public static void paintComboboxFocusRing(@NotNull final Graphics2D g2d, @NotNull final Rectangle bounds) {
    final Color color = getFocusRingColor();
    final Color[] colors = new Color[]{
      ColorUtil.toAlpha(color, 180),
      ColorUtil.toAlpha(color, 130),
      ColorUtil.toAlpha(color, 80),
      ColorUtil.toAlpha(color, 80)
    };

    final Object oldAntialiasingValue = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    final Object oldStrokeControlValue = g2d.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL);

    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                         USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

    int _y = MAC_COMBO_BORDER_V_OFFSET;
    
    final GeneralPath path1 = new GeneralPath();
    path1.moveTo(2, _y + 4);
    path1.quadTo(2, +_y + 2, 4, _y + 2);
    path1.lineTo(bounds.width - 7, _y + 2);
    path1.quadTo(bounds.width - 5, _y + 3, bounds.width - 4, _y + 5);
    path1.lineTo(bounds.width - 4, bounds.height - 7 + _y);
    path1.quadTo(bounds.width - 5, bounds.height - 5 + _y, bounds.width - 7, bounds.height - 4 + _y);
    path1.lineTo(4, bounds.height - 4 + _y);
    path1.quadTo(2, bounds.height - 4 + _y, 2, bounds.height - 6 + _y);
    path1.closePath();

    g2d.setColor(colors[0]);
    g2d.draw(path1);

    final GeneralPath path2 = new GeneralPath();
    path2.moveTo(1, 5 + _y);
    path2.quadTo(1, 1 + _y, 5, 1 + _y);
    path2.lineTo(bounds.width - 8, 1 + _y);
    path2.quadTo(bounds.width - 4, 2 + _y, bounds.width - 3, 6 + _y);
    path2.lineTo(bounds.width - 3, bounds.height - 7 + _y);
    path2.quadTo(bounds.width - 4, bounds.height - 4 + _y, bounds.width - 8, bounds.height - 3 + _y);
    path2.lineTo(4, bounds.height - 3 + _y);
    path2.quadTo(1, bounds.height - 3 + _y, 1, bounds.height - 6 + _y);
    path2.closePath();

    g2d.setColor(colors[1]);
    g2d.draw(path2);

    final GeneralPath path3 = new GeneralPath();
    path3.moveTo(0, 4 + _y);
    path3.quadTo(0, _y, 7, _y);
    path3.lineTo(bounds.width - 9, _y);
    path3.quadTo(bounds.width - 2, 1 + _y, bounds.width - 2, 7 + _y);
    path3.lineTo(bounds.width - 2, bounds.height - 8 + _y);
    path3.quadTo(bounds.width - 3, bounds.height - 1 + _y, bounds.width - 12, bounds.height - 2 + _y);
    path3.lineTo(7, bounds.height - 2 + _y);
    path3.quadTo(0, bounds.height - 1 + _y, 0, bounds.height - 7 + _y);
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
          paintComboboxFocusRing((Graphics2D)g, combobox.getBounds());
        }
      }
    }
  }

  @Deprecated
  public static void doNotFillBackground(@NotNull final JTree tree, @NotNull final DefaultTreeCellRenderer renderer) {
    if (WideSelectionTreeUI.isWideSelection(tree)) {
        renderer.setOpaque(false);
        try {
          final Field fillBackground = DefaultTreeCellRenderer.class.getDeclaredField("fillBackground");
          fillBackground.setAccessible(true);
          fillBackground.set(renderer, false);
        }
        catch (Exception e) {
          // nothing
        }
    }
  }

  public static Cursor getInvertedTextCursor() {
    if (INVERTED_TEXT_CURSOR == null) {
      final Toolkit toolkit = Toolkit.getDefaultToolkit();
      Image cursorImage = toolkit.getImage(MacUIUtil.class.getClassLoader().getResource("/mac/text.png")); // will also load text@2x.png
      INVERTED_TEXT_CURSOR = toolkit.createCustomCursor(cursorImage, new Point(15, 13), "InvertedTextCursor");
    }
    return INVERTED_TEXT_CURSOR;
  }
}
