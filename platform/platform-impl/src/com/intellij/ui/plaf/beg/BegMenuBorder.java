
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
package com.intellij.ui.plaf.beg;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.UIResource;
import java.awt.*;

public class BegMenuBorder extends AbstractBorder implements UIResource {
  protected static Insets borderInsets = new Insets(2, 2, 2, 2);

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

  public Insets getBorderInsets(Component c) {
    return (Insets)borderInsets.clone();
  }
}
