/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.TreeUI;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;
import java.awt.geom.GeneralPath;
import java.lang.reflect.Field;

/**
 * User: spLeaner
 */
public class MacUIUtil {

  public static final boolean USE_QUARTZ = "true".equals(System.getProperty("apple.awt.graphics.UseQuartz"));
  public static final String MAC_FILL_BORDER = "MAC_FILL_BORDER";
  public static final int MAC_COMBO_BORDER_V_OFFSET = SystemInfo.isMacOSLion ? 1 : 0;
  private static Cursor INVERTED_TEXT_CURSOR;

  private MacUIUtil() {
  }

  public static void paintFocusRing(Graphics2D g2d, Color ringColor, Rectangle bounds) {
    paintFocusRing(g2d, ringColor, bounds, false);
  }

  public static void paintFocusRing(Graphics2D g, Color ringColor, Rectangle bounds, boolean oval) {
    int correction = UIUtil.isUnderDarcula() ? 50 : 0;
    final Color[] colors = new Color[]{
      ColorUtil.toAlpha(ringColor, 180 - correction),
      ColorUtil.toAlpha(ringColor, 120 - correction),
      ColorUtil.toAlpha(ringColor, 70  - correction),
      ColorUtil.toAlpha(ringColor, 100 - correction),
      ColorUtil.toAlpha(ringColor, 50  - correction)
    };

    final Object oldAntialiasingValue = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    final Object oldStrokeControlValue = g.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL);

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, !oval &&
                         USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);


    final Rectangle r = new Rectangle(bounds.x - 3, bounds.y - 3, bounds.width + 6, bounds.height + 6);

    g.setColor(colors[0]);
    drawRectOrOval(g, oval, 5, r.x + 2, r.y + 2, r.width - 5, r.height - 5);

    g.setColor(colors[1]);
    drawRectOrOval(g, oval, 7, r.x + 1, r.y + 1, r.width - 3, r.height - 3);

    g.setColor(colors[2]);
    drawRectOrOval(g, oval, 9, r.x, r.y, r.width - 1, r.height - 1);

    g.setColor(colors[3]);
    drawRectOrOval(g, oval, 0, r.x + 3, r.y + 3, r.width - 7, r.height - 7);

    g.setColor(colors[4]);
    drawRectOrOval(g, oval, 0, r.x + 4, r.y + 4, r.width - 9, r.height - 9);

    // restore rendering hints
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasingValue);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, oldStrokeControlValue);
  }

  private static void drawRectOrOval(Graphics2D g, boolean oval, int arc, int x, int y, int width, int height) {
    if (oval) {
      g.drawOval(x, y, width, height);
    } else {
      if (arc == 0) {
        g.drawRect(x, y, width, height);
      } else {
        g.drawRoundRect(x, y, width, height, arc, arc);
      }
    }
  }

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

  public static class EditorTextFieldBorder implements Border {
    private JComponent myEnabledComponent;

    public EditorTextFieldBorder(final JComponent enabledComponent) {
      myEnabledComponent = enabledComponent;
    }

    @Override
    public void paintBorder(final Component c, final Graphics g, final int x, final int y, final int width, final int height) {
      final int x1 = x + 3;
      final int y1 = y + 3;
      final int width1 = width - 8;
      final int height1 = height - 6;

      if (c.isOpaque() || (c instanceof JComponent && ((JComponent)c).getClientProperty(MAC_FILL_BORDER) == Boolean.TRUE)) {
        g.setColor(UIUtil.getPanelBackground());
        g.fillRect(x, y, width, height);
      }

      g.setColor(c.getBackground());
      g.fillRect(x1, y1, width1, height1);

      if (!myEnabledComponent.isEnabled()) {
        ((Graphics2D)g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
      }

      g.setColor(new Color(100, 100, 100, 200));
      g.drawRect(x1, y1, width1 - 1, height1 - 1);

      g.setColor(Gray._225);
      g.drawRect(x1 + 1, y1 + 1, width1 - 3, height1 - 3);

      if (myEnabledComponent.isEnabled() && myEnabledComponent.isVisible() && hasFocus(myEnabledComponent)) {
        paintTextFieldFocusRing((Graphics2D) g, new Rectangle(x1, y1, width1, height1));
      }
    }

    private static boolean hasFocus(@NotNull final Component toCheck) {
      if (toCheck.hasFocus()) return true;
      if (toCheck instanceof JComponent) {
        final JComponent c = (JComponent)toCheck;
        for (int i = 0; i < c.getComponentCount(); i++) {
          final boolean b = hasFocus(c.getComponent(i));
          if (b) return true;
        }
      }

      return false;
    }


    @Override
    public Insets getBorderInsets(Component c) {
      return new Insets(6, 7, 6, 7);
    }

    @Override
    public boolean isBorderOpaque() {
      return true;
    }
  }

  public static Color getFocusRingColor() {
    final Object o = UIManager.get("Focus.color");
    if (o instanceof Color) {
      return (Color)o;
    }

    return new Color(64, 113, 167);
  }

  public static void paintTextFieldFocusRing(@NotNull final Graphics2D g2d, @NotNull final Rectangle bounds) {
    paintFocusRing(g2d, getFocusRingColor(), bounds);
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

  public static void doNotFillBackground(@NotNull final JTree tree, @NotNull final DefaultTreeCellRenderer renderer) {
    TreeUI ui = tree.getUI();
    if (ui instanceof WideSelectionTreeUI) {
      if (((WideSelectionTreeUI)ui).isWideSelection()) {
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
  }

  public static Cursor getInvertedTextCursor() {
    if (INVERTED_TEXT_CURSOR == null) {
      final Toolkit toolkit = Toolkit.getDefaultToolkit();
      Image cursorImage = toolkit.createImage(MacUIUtil.class.getClassLoader().getResource("/mac/text.gif"));
      INVERTED_TEXT_CURSOR = toolkit.createCustomCursor(cursorImage, new Point(15, 13), "InvertedTextCursor");
    }
    return INVERTED_TEXT_CURSOR;
  }
}
