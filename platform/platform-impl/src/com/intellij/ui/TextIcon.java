// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.ui.AntialiasingType;

import javax.swing.*;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;

import static com.intellij.ide.ui.UISettings.setupAntialiasing;
import static com.intellij.ui.paint.RectanglePainter.FILL;
import static java.awt.RenderingHints.VALUE_FRACTIONALMETRICS_OFF;

/**
 * @author Sergey.Malenkov
 */
public final class TextIcon implements Icon {
  private static final FontRenderContext CONTEXT =
    new FontRenderContext(null, AntialiasingType.getKeyForCurrentScope(false), VALUE_FRACTIONALMETRICS_OFF);

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
        setupAntialiasing(g);
        g.drawString(myText, myInsets.left + x - bounds.x, myInsets.top + y - bounds.y);
      }
      finally {
        g.dispose();
      }
    }
  }
}
