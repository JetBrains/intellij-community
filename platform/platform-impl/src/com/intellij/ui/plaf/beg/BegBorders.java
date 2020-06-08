// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.plaf.beg;


import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.text.JTextComponent;
import java.awt.*;

/**
 * @author Eugene Belyaev
 */
public final class BegBorders {
  private static Border ourTextFieldBorder;
  private static Border ourScrollPaneBorder;

  public static Border getTextFieldBorder() {
    if (ourTextFieldBorder == null) {
      ourTextFieldBorder = new BorderUIResource.CompoundBorderUIResource(
        new TextFieldBorder(),
        BorderFactory.createEmptyBorder(2, 2, 2, 2));
    }
    return ourTextFieldBorder;
  }

  public static Border getScrollPaneBorder() {
    if (ourScrollPaneBorder == null) {
      ourScrollPaneBorder = new BorderUIResource.LineBorderUIResource(MetalLookAndFeel.getControlDarkShadow());
    }
    return ourScrollPaneBorder;
  }

  public static class TextFieldBorder extends LineBorder implements UIResource {
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
        }
        else {
          drawDisabledBorder(g, x, y, w, h);
        }
        return;
      }

      if (c.isEnabled() && ((JTextComponent)c).isEditable()) {
        drawLineBorder(g, x, y, w, h);
      }
      else {
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
}
