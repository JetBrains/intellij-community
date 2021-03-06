// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.util.Objects;

import static com.intellij.ui.paint.RectanglePainter.FILL;
import static com.intellij.util.ui.UIUtil.getLcdContrastValue;

public final class TextIcon implements Icon {
  @SuppressWarnings("UseDPIAwareInsets")
  private final Insets myInsets = new Insets(0, 0, 0, 0);
  private Integer myRound;
  private Color myBackground;
  private Color myForeground;
  private Font myFont;
  private String myText;
  private Rectangle myTextBounds;
  private FontRenderContext myContext;

  private Rectangle getTextBounds() {
    if (myTextBounds == null && myFont != null && myText != null && !myText.isEmpty()) {
      Object aaHint = UIManager.get(RenderingHints.KEY_TEXT_ANTIALIASING);
      if (aaHint == null) aaHint = RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT;
      Object fmHint = UIManager.get(RenderingHints.KEY_FRACTIONALMETRICS);
      if (fmHint == null) fmHint = RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT;
      myContext = new FontRenderContext(null, aaHint, fmHint);
      myTextBounds = getPixelBounds(myFont, myText, myContext);
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
      Graphics2D g2d = (Graphics2D)g.create(myInsets.left + x, myInsets.top + y, bounds.width, bounds.height);
      try {
        Object textLcdContrast = UIManager.get(RenderingHints.KEY_TEXT_LCD_CONTRAST);
        if (textLcdContrast == null) textLcdContrast = getLcdContrastValue(); // L&F is not properly updated
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, myContext.getAntiAliasingHint());
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, textLcdContrast);
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, myContext.getFractionalMetricsHint());
        g2d.setColor(myForeground);
        g2d.setFont(myFont);
        g2d.drawString(myText, -bounds.x, -bounds.y);
      }
      finally {
        g2d.dispose();
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TextIcon icon = (TextIcon)o;
    return myInsets.equals(icon.myInsets) &&
           Objects.equals(myRound, icon.myRound) &&
           Objects.equals(myBackground, icon.myBackground) &&
           Objects.equals(myForeground, icon.myForeground) &&
           Objects.equals(myFont, icon.myFont) &&
           Objects.equals(myText, icon.myText) &&
           Objects.equals(myTextBounds, icon.myTextBounds);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myInsets, myRound, myBackground, myForeground, myFont, myText, myTextBounds);
  }

  private static Rectangle getPixelBounds(Font font, String text, FontRenderContext context) {
    return font.hasLayoutAttributes()
           ? new TextLayout(text, font, context).getPixelBounds(context, 0, 0)
           : font.createGlyphVector(context, text).getPixelBounds(context, 0, 0);
  }
}
