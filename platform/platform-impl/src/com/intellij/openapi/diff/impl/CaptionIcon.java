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
package com.intellij.openapi.diff.impl;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;

public class CaptionIcon implements Icon {
  private final Component mySomeComponent;
  private final Form myForm;
  private final boolean myWithContinuation;
  private final boolean myEmphasize;
  private final Color myBgrnd;
  private Font myFont;
  private final String myText;

  private int myHeight;
  private int myWidth;
  private Font myPlusFont;
  private int myAddWidth;

  public CaptionIcon(Color bgrnd, Font font, String text, final Component someComponent, Form form, boolean withContionuation,
                     final boolean emphasize) {
    myBgrnd = bgrnd;
    myFont = font;
    myText = text;
    mySomeComponent = someComponent;
    myForm = form;
    myWithContinuation = withContionuation;
    myEmphasize = emphasize;

    fontBasedCalculations();
  }

  private void fontBasedCalculations() {
    final FontMetrics fm = mySomeComponent.getFontMetrics(myFont);
    final Rectangle2D bounds = fm.getStringBounds(myText, mySomeComponent.getGraphics());
    final double height = bounds.getHeight() - fm.getMaxDescent();  // +-
    myPlusFont = myFont.deriveFont(Font.BOLD);
    if (myWithContinuation) {
      myAddWidth = mySomeComponent.getFontMetrics(myPlusFont).stringWidth(" +");
    } else {
      myAddWidth = 0;
    }
    myHeight = (int) height + 4;
    myWidth = (int) bounds.getWidth() + 4 + myAddWidth;
  }

  public void setFont(Font font) {
    myFont = font;
    fontBasedCalculations();
  }

  public int getIconHeight() {
    return myHeight;
  }

  public void paintIcon(Component c, Graphics g, int x, int y) {
    final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);

    ((Graphics2D)g).setPaint(myBgrnd);

    myForm.draw(c, (Graphics2D)g, myWidth, myHeight, x, y, myWithContinuation, myEmphasize);

    g.setFont(myFont);
    g.setColor(UIUtil.getTextAreaForeground());
    g.drawString(myText, x + 4, y + myHeight - 3);  // -2

    g.setColor(myBgrnd.darker().darker());
    g.setFont(myPlusFont);
    if (myWithContinuation) {
      g.drawString(" +", x + myWidth - 2 - myAddWidth, y + myHeight - 3);
    }

    config.restore();
  }

  public int getIconWidth() {
    return myWidth + 4;
  }

  public static enum Form {
    SQUARE() {
      @Override
      public void draw(Component c, Graphics2D g, int width, int height, int x, int y, boolean withPlus, boolean emphasize) {
        g.fillRect(x + 2, y, width, height);
        g.setColor(new JBColor(Color.gray, Color.gray.darker()));
        g.drawRect(x + 2, y, width, height - 1);
      }
    },
    ROUNDED() {
      @Override
      public void draw(Component c, Graphics2D g, int width, int height, int x, int y, boolean withPlus, boolean emphasize) {
        g.fillRoundRect(x + 2, y, width, height, 5, 5);

        final BasicStroke stroke = new BasicStroke(1);
        g.setColor(new JBColor(Color.gray, Color.gray.darker()));
        g.setStroke(stroke);
        g.drawRoundRect(x + 2, y, width, height - 1, 5, 5);
      }
    };

    public abstract void draw(Component c, Graphics2D g, int width, int height, int x, int y, final boolean withPlus, boolean emphasize);
  }
}
