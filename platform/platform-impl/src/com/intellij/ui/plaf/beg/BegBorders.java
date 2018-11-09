// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.plaf.beg;


import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicBorders;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.text.JTextComponent;
import java.awt.*;

/**
 * @author Eugene Belyaev
 */
public class BegBorders {
  private static Border ourButtonBorder;
  private static Border ourTextFieldBorder;
  private static Border ourScrollPaneBorder;

  public static Border getButtonBorder() {
    if (ourButtonBorder == null) {
      ourButtonBorder = new BorderUIResource.CompoundBorderUIResource(
        new ButtonBorder(),
        new BasicBorders.MarginBorder());
    }
    return ourButtonBorder;
  }

  public static Border getTextFieldBorder() {
    if (ourTextFieldBorder == null) {
      ourTextFieldBorder = new BorderUIResource.CompoundBorderUIResource(
        new TextFieldBorder(),
        //new FlatLineBorder(),
        BorderFactory.createEmptyBorder(2, 2, 2, 2));
    }
    return ourTextFieldBorder;
  }

  public static Border getScrollPaneBorder() {
    if (ourScrollPaneBorder == null) {
      ourScrollPaneBorder = new BorderUIResource.LineBorderUIResource(MetalLookAndFeel.getControlDarkShadow());
      //ourScrollPaneBorder = new FlatLineBorder();
    }
    return ourScrollPaneBorder;
  }


  public static class FlatLineBorder extends LineBorder implements UIResource {
    public FlatLineBorder() {
      super(new Color(127, 157, 185), 1, true);
    }
  }

  public static class ButtonBorder extends AbstractBorder implements UIResource {

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
      AbstractButton button = (AbstractButton) c;
      ButtonModel model = button.getModel();

      if (model.isEnabled()) {
        boolean isPressed = model.isPressed() && model.isArmed();
        boolean isDefault = (button instanceof JButton && ((JButton) button).isDefaultButton());

        if (isPressed && isDefault) {
          drawDefaultButtonPressedBorder(g, x, y, w, h);
        } else if (isPressed) {
          drawPressed3DBorder(g, x, y, w, h);
        } else if (isDefault) {
          drawDefaultButtonBorder(g, x, y, w, h, false);
        } else {
          drawButtonBorder(g, x, y, w, h, false);
        }
      } else { // disabled state
        drawDisabledBorder(g, x, y, w - 1, h - 1);
      }
    }

    @Override
    public Insets getBorderInsets(Component c) {
      return JBUI.insets(3);
    }

    @Override
    public Insets getBorderInsets(Component c, Insets newInsets) {
      Insets borderInsets = JBUI.insets(3);
      newInsets.top = borderInsets.top;
      newInsets.left = borderInsets.left;
      newInsets.bottom = borderInsets.bottom;
      newInsets.right = borderInsets.right;
      return newInsets;
    }
  }

  public static class ScrollPaneBorder extends AbstractBorder implements UIResource {
    private static final Insets insets = new Insets(1, 1, 2, 2);

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y,
                            int w, int h) {
      JScrollPane scroll = (JScrollPane) c;
      JComponent colHeader = scroll.getColumnHeader();
      int colHeaderHeight = 0;
      if (colHeader != null)
        colHeaderHeight = colHeader.getHeight();

      JComponent rowHeader = scroll.getRowHeader();
      int rowHeaderWidth = 0;
      if (rowHeader != null)
        rowHeaderWidth = rowHeader.getWidth();

      /*
      g.translate(x, y);

      g.setColor(MetalLookAndFeel.getControlDarkShadow());
      g.drawRect(0, 0, w - 2, h - 2);
      g.setColor(MetalLookAndFeel.getControlHighlight());

      g.drawLine(w - 1, 1, w - 1, h - 1);
      g.drawLine(1, h - 1, w - 1, h - 1);

      g.setColor(MetalLookAndFeel.getControl());
      if (colHeaderHeight > 0) {
        g.drawLine(w - 2, 2 + colHeaderHeight, w - 2, 2 + colHeaderHeight);
      }
      if (rowHeaderWidth > 0) {
        g.drawLine(1 + rowHeaderWidth, h - 2, 1 + rowHeaderWidth, h - 2);
      }

      g.translate(-x, -y);
      */

      drawLineBorder(g, x, y, w, h);
    }

    @Override
    public Insets getBorderInsets(Component c) {
      return (Insets)insets.clone();
    }
  }

  public static class TextFieldBorder /*extends MetalBorders.Flush3DBorder*/  extends LineBorder implements UIResource {
    public TextFieldBorder() {
      super(null, 1);
    }

    @Override
    public boolean isBorderOpaque() {
      return false;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y,
                            int w, int h) {
      if (!(c instanceof JTextComponent)) {
        // special case for non-text components (bug ID 4144840)
        if (c.isEnabled()) {
          drawFlush3DBorder(g, x, y, w, h);
        } else {
          drawDisabledBorder(g, x, y, w, h);
        }
        return;
      }

      if (c.isEnabled() && ((JTextComponent) c).isEditable()) {
        drawLineBorder(g, x, y, w, h);
        /*
        g.translate(x, y);
        g.setColor(MetalLookAndFeel.getControlDarkShadow());
        g.drawLine(1, 0, w - 2, 0);
        g.drawLine(0, 1, 0, h - 2);
        g.drawLine(w - 1, 1, w - 1, h - 2);
        g.drawLine(1, h - 1, w - 2, h - 1);
        g.translate(-x, -y);
        */

      } else {
        drawDisabledBorder(g, x, y, w, h);
      }
    }
  }

  static void drawLineBorder(Graphics g, int x, int y, int w, int h) {
    g.translate(x, y);
    g.setColor(MetalLookAndFeel.getControlDarkShadow());
    g.drawRect(0, 0, w - 1, h - 1);
    g.translate(-x, -y);
  }

  static void drawFlush3DBorder(Graphics g, int x, int y, int w, int h) {
    g.translate(x, y);
    g.setColor(MetalLookAndFeel.getControlHighlight());
    g.drawRect(1, 1, w - 2, h - 2);
    g.setColor(MetalLookAndFeel.getControlDarkShadow());
    g.drawRect(0, 0, w - 2, h - 2);
    g.translate(-x, -y);
  }

  static void drawDisabledBorder(Graphics g, int x, int y, int w, int h) {
    g.translate(x, y);
    g.setColor(MetalLookAndFeel.getControlShadow());
    g.drawRect(0, 0, w - 1, h - 1);
    g.translate(-x, -y);
  }

  static void drawDefaultButtonPressedBorder(Graphics g, int x, int y, int w, int h) {
    drawPressed3DBorder(g, x + 1, y + 1, w - 1, h - 1);
    g.translate(x, y);
    g.setColor(MetalLookAndFeel.getControlDarkShadow());
    g.drawRect(0, 0, w - 3, h - 3);
    UIUtil.drawLine(g, w - 2, 0, w - 2, 0);
    UIUtil.drawLine(g, 0, h - 2, 0, h - 2);
    g.translate(-x, -y);
  }

  static void drawPressed3DBorder(Graphics g, int x, int y, int w, int h) {
    g.translate(x, y);

    drawFlush3DBorder(g, 0, 0, w, h);

    g.setColor(MetalLookAndFeel.getControlShadow());
    UIUtil.drawLine(g, 1, 1, 1, h - 1);
    UIUtil.drawLine(g, 1, 1, w - 1, 1);
    g.translate(-x, -y);
  }

  static void drawDefaultButtonBorder(Graphics g, int x, int y, int w, int h, boolean active) {
    drawButtonBorder(g, x + 1, y + 1, w - 1, h - 1, active);
    g.translate(x, y);
    g.setColor(MetalLookAndFeel.getControlDarkShadow());
    g.drawRect(0, 0, w - 3, h - 3);
    UIUtil.drawLine(g, w - 2, 0, w - 2, 0);
    UIUtil.drawLine(g, 0, h - 2, 0, h - 2);
    g.translate(-x, -y);
  }

  static void drawButtonBorder(Graphics g, int x, int y, int w, int h, boolean active) {
    if (active) {
      drawActiveButtonBorder(g, x, y, w, h);
    } else {
      drawFlush3DBorder(g, x, y, w, h);
      /*
      drawLineBorder(g, x, y, w - 1, h - 1);
      g.setColor(MetalLookAndFeel.getControlHighlight());
      g.drawLine(x + 1, y + h - 1, x + w - 1, y + h - 1);
      g.drawLine(x + w - 1, y + 1, x + w - 1, y + h - 1);
      */
    }
  }

  static void drawActiveButtonBorder(Graphics g, int x, int y, int w, int h) {
    drawFlush3DBorder(g, x, y, w, h);
    g.setColor(MetalLookAndFeel.getPrimaryControl());
    UIUtil.drawLine(g, x + 1, y + 1, x + 1, h - 3);
    UIUtil.drawLine(g, x + 1, y + 1, w - 3, x + 1);
    g.setColor(MetalLookAndFeel.getPrimaryControlDarkShadow());
    UIUtil.drawLine(g, x + 2, h - 2, w - 2, h - 2);
    UIUtil.drawLine(g, w - 2, y + 2, w - 2, h - 2);
  }
}
