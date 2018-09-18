
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.plaf.beg;

import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.UIResource;
import java.awt.*;

public class BegMenuBorder extends AbstractBorder implements UIResource {
  protected static Insets borderInsets = JBUI.insets(2);

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
    JMenuItem b = (JMenuItem)c;
    ButtonModel model = b.getModel();

    g.translate(x, y);
    if (c.getParent() instanceof JMenuBar){
      if (model.isArmed() || model.isSelected()){
        /*
        g.setColor( MetalLookAndFeel.getControlDarkShadow() );
        g.drawLine( 0, 0, w - 2, 0 );
        g.drawLine( 0, 0, 0, h - 1 );
        g.drawLine( w - 2, 2, w - 2, h - 1 );

        g.setColor( MetalLookAndFeel.getPrimaryControlHighlight() );
        g.drawLine( w - 1, 1, w - 1, h - 1 );

        g.setColor( MetalLookAndFeel.getMenuBackground() );
        g.drawLine( w - 1, 0, w - 1, 0 );
        */
      }
    }
    else{
      if (model.isArmed() || (c instanceof JMenu && model.isSelected())){
        /*
        g.setColor( MetalLookAndFeel.getPrimaryControlDarkShadow() );
        g.drawLine( 0, 0, w - 1, 0 );

        g.setColor( MetalLookAndFeel.getPrimaryControlHighlight() );
        g.drawLine( 0, h - 1, w - 1, h - 1 );
        */
      }
      else{
        /*
        g.setColor( MetalLookAndFeel.getPrimaryControlHighlight() );
        g.drawLine( 0, 0, 0, h - 1 );
        */
      }
    }
    g.translate(-x, -y);
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return (Insets)borderInsets.clone();
  }
}
