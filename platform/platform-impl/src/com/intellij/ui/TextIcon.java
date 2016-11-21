/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ui;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import javax.swing.Icon;

import static com.intellij.ui.paint.RectanglePainter.FILL;

/**
 * @author Sergey.Malenkov
 */
public final class TextIcon implements Icon {
  private static final FontRenderContext CONTEXT = new FontRenderContext(null, true, false);

  @SuppressWarnings("UseDPIAwareInsets")
  private final Insets myInsets = new Insets(0, 0, 0, 0);
  private Integer myRound;
  private Color myBackground;
  private Color myForeground;
  private Font myFont;
  private String myText;
  private Rectangle myTextBounds;

  private Rectangle getTextBounds() {
    if (myTextBounds == null && myFont != null && myText != null && !myText.isEmpty()) {
      GlyphVector vector = myFont.createGlyphVector(CONTEXT, myText);
      myTextBounds = vector.getPixelBounds(CONTEXT, 0, 0);
    }
    return myTextBounds;
  }

  public TextIcon(String text, Color foreground, Color background, int margin) {
    setInsets(margin, margin, margin, margin);
    setRound(margin * 4);
    setBackground(background);
    setForeground(foreground);
    setText(text);
  }

  public void setInsets(int top, int left, int bottom, int right) {
    myInsets.set(top, left, bottom, right);
  }

  public void setInsets(Insets insets) {
    myInsets.set(insets.top, insets.left, insets.bottom, insets.right);
  }

  public void setRound(int round) {
    myRound = round;
  }

  public void setBackground(Color background) {
    myBackground = background;
  }

  public void setForeground(Color foreground) {
    myForeground = foreground;
  }

  public void setText(String text) {
    myTextBounds = null;
    myText = text;
  }

  public void setFont(Font font) {
    myFont = font;
    myTextBounds = null;
  }

  @Override
  public int getIconWidth() {
    Rectangle bounds = getTextBounds();
    return bounds == null ? 0 : myInsets.left + bounds.width + myInsets.right;
  }

  @Override
  public int getIconHeight() {
    Rectangle bounds = getTextBounds();
    return bounds == null ? 0 : myInsets.top + bounds.height + myInsets.bottom;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    if (myBackground != null && g instanceof Graphics2D) {
      g.setColor(myBackground);
      FILL.paint((Graphics2D)g, x, y, getIconWidth(), getIconHeight(), myRound);
    }
    Rectangle bounds = getTextBounds();
    if (myForeground != null && bounds != null) {
      g = g.create();
      try {
        g.setColor(myForeground);
        g.setFont(myFont);
        if (g instanceof Graphics2D) {
          Graphics2D g2d = (Graphics2D)g;
          g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, CONTEXT.getAntiAliasingHint());
          g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, CONTEXT.getFractionalMetricsHint());
        }
        g.drawString(myText, myInsets.left + x - bounds.x, myInsets.top + y - bounds.y);
      }
      finally {
        g.dispose();
      }
    }
  }
}
