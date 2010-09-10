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

  private MacUIUtil() {
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

      g.setColor(UIUtil.getPanelBackgound());
      g.fillRect(x, y, width, height);

      g.setColor(c.getBackground());
      g.fillRect(x1, y1, width1, height1);
      
      if (!myEnabledComponent.isEnabled()) {
        ((Graphics2D)g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
      }

      g.setColor(new Color(100, 100, 100, 200));
      g.drawLine(x1, y1, x1 + width1 - 1, y1);

      g.setColor(new Color(212, 212, 212, 200));
      g.drawLine(x1, y1 + 1, x1 + width1 - 1, y1 + 1);

      g.setColor(new Color(225, 225, 225));
      g.drawLine(x1 + 1, y1 + height1 - 1, x1 + width1 - 2, y1 + height1 - 1);

      g.setColor(new Color(30, 30, 30, 70));
      g.drawLine(x1, y1, x1, y1 + height1 - 1);
      g.drawLine(x1 + width1 - 1, y1, x1 + width1 - 1, y1 + height1 - 1);

      g.setColor(new Color(30, 30, 30, 10));
      g.drawLine(x1 + 1, y1, x1 + 1, y1 + height1 - 1);
      g.drawLine(x1 + width1 - 2, y1, x1 + width1 - 2, y1 + height1 - 1);

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
    final Color color = getFocusRingColor();
    final Color[] colors = new Color[]{
      new Color(color.getRed(), color.getGreen(), color.getBlue(), 180),
      new Color(color.getRed(), color.getGreen(), color.getBlue(), 120),
      new Color(color.getRed(), color.getGreen(), color.getBlue(), 70),
      new Color(color.getRed(), color.getGreen(), color.getBlue(), 100),
      new Color(color.getRed(), color.getGreen(), color.getBlue(), 50)
    };

    final Object oldAntialiasingValue = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    final Object oldStrokeControlValue = g2d.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL);

    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                         USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);


    final Rectangle r = new Rectangle(bounds.x - 3, bounds.y - 3, bounds.width + 6, bounds.height + 6);

    g2d.setColor(colors[0]);
    g2d.drawRoundRect(r.x + 2, r.y + 2, r.width - 5, r.height - 5, 5, 5);

    g2d.setColor(colors[1]);
    g2d.drawRoundRect(r.x + 1, r.y + 1, r.width - 3, r.height - 3, 7, 7);

    g2d.setColor(colors[2]);
    g2d.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, 9, 9);

    g2d.setColor(colors[3]);
    g2d.drawRect(r.x + 3, r.y + 3, r.width - 7, r.height - 7);

    g2d.setColor(colors[4]);
    g2d.drawRect(r.x + 4, r.y + 4, r.width - 9, r.height - 9);

    // restore rendering hints
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasingValue);
    g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, oldStrokeControlValue);
  }

  public static void paintComboboxFocusRing(@NotNull final Graphics2D g2d, @NotNull final Rectangle bounds) {
    final Color color = getFocusRingColor();
    final Color[] colors = new Color[]{
      new Color(color.getRed(), color.getGreen(), color.getBlue(), 180),
      new Color(color.getRed(), color.getGreen(), color.getBlue(), 130),
      new Color(color.getRed(), color.getGreen(), color.getBlue(), 80),
      new Color(color.getRed(), color.getGreen(), color.getBlue(), 80)
    };

    final Object oldAntialiasingValue = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    final Object oldStrokeControlValue = g2d.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL);

    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                         USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

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
          paintComboboxFocusRing((Graphics2D)g, combobox.getBounds());
        }
      }
    }
  }

  public static void doNotFillBackground(@NotNull final JTree tree, @NotNull final DefaultTreeCellRenderer renderer) {
    TreeUI ui = tree.getUI();
    if (ui instanceof UIUtil.MacTreeUI) {
      if (((UIUtil.MacTreeUI)ui).isWideSelection()) {
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
}
