// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.diagnostic.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.Objects;

import static com.intellij.ui.paint.RectanglePainter.DRAW;
import static com.intellij.ui.paint.RectanglePainter.FILL;
import static com.intellij.util.ui.UIUtil.getLcdContrastValue;

public final class TextIcon implements Icon {
  private static final Logger LOG = Logger.getInstance(TextIcon.class);

  @SuppressWarnings("UseDPIAwareInsets")
  private final Insets myInsets = new Insets(0, 0, 0, 0);
  private Integer myRound;
  private Boolean withBorders;
  private Color myBackground;
  private Color myForeground;
  private Color myBorderColor;
  private Font myFont;
  private String myText;
  private Rectangle myTextBounds;
  private FontRenderContext myContext;
  private AffineTransform myFontTransform;

  private Rectangle getTextBounds() {
    if (myTextBounds == null && myFont != null && myText != null && !myText.isEmpty()) {
      myContext = createContext();
      Font fnt = myFontTransform == null ? myFont : myFont.deriveFont(myFontTransform);
      myTextBounds = getPixelBounds(fnt, myText, myContext);

      if (myFontTransform != null) {
        try {
          AffineTransform reverseTransform = myFontTransform.createInverse();
          myTextBounds = applyTransform(myTextBounds, reverseTransform);
        }
        catch (NoninvertibleTransformException e) {
          LOG.error(e);
        }
      }
    }
    return myTextBounds;
  }

  public TextIcon(String text, Color foreground, Color background, int margin) {
    this(text, foreground, background, margin, false);
  }

  public TextIcon(String text, Color foreground, Color background, int margin, boolean withBorders) {
    this(text, foreground, background, margin, withBorders, withBorders ? 255 : 0);
  }

  public TextIcon(String text, Color foreground, Color background, int margin, boolean withBorders, int borderAlpha) {
    this(text, foreground, background, ColorUtil.toAlpha(background, borderAlpha), margin, withBorders);
  }

  public TextIcon(String text, Color foreground, Color background, Color borderColor, int margin, boolean withBorders) {
    setWithBorders(withBorders);
    setBackground(background);
    setForeground(foreground);
    setBorderColor(borderColor);
    setInsets(margin, margin, margin, margin);
    setRound(margin * 4);
    setText(text);
  }

  public void setFontTransform(AffineTransform fontTransform) {
    myFontTransform = fontTransform;
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

  public void setWithBorders(boolean withBorders) {
    this.withBorders = withBorders;
  }

  public void setBackground(Color background) {
    myBackground = background;
  }

  public void setForeground(Color foreground) {
    myForeground = foreground;
  }

  public void setBorderColor(Color borderColor) {
    myBorderColor = borderColor;
  }

  public void setText(String text) {
    myTextBounds = null;
    myText = text;
  }

  public void setFont(Font font) {
    myFont = font;
    myTextBounds = null;
  }

  public Insets getInsets() {
    return myInsets;
  }

  public Integer getRound() {
    return myRound;
  }

  public Boolean getWithBorders() {
    return withBorders;
  }

  public Color getBackground() {
    return myBackground;
  }

  public Color getForeground() {
    return myForeground;
  }

  public Color getBorderColor() {
    return myBorderColor;
  }

  public Font getFont() {
    return myFont;
  }

  public String getText() {
    return myText;
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
    if (g instanceof Graphics2D) {
      if (myBackground != null) {
        g.setColor(myBackground);
        FILL.paint((Graphics2D)g, x, y, getIconWidth(), getIconHeight(), myRound);
      }
      if (withBorders && myBorderColor != null) {
        g.setColor(myBorderColor);
        DRAW.paint((Graphics2D)g, x, y, getIconWidth(), getIconHeight(), myRound);
      }
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
           Objects.equals(myTextBounds, icon.myTextBounds) &&
           Objects.equals(withBorders, icon.withBorders) &&
           Objects.equals(myBorderColor, icon.myBorderColor);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myInsets, myRound, myBackground, myForeground, myFont, myText, myTextBounds, withBorders, myBorderColor);
  }

  private static Rectangle applyTransform(Rectangle srcRect, AffineTransform at) {
    Point2D leftTop = at.transform(new Point(srcRect.x, srcRect.y), null);
    Point2D rightBottom = at.transform(new Point(srcRect.x + srcRect.width, srcRect.y + srcRect.height), null);
    int left = (int)Math.floor(leftTop.getX());
    int top = (int)Math.floor(leftTop.getY());
    int right = (int)Math.ceil(rightBottom.getX());
    int bottom = (int)Math.ceil(rightBottom.getY());
    return new Rectangle(left, top, right - left, bottom - top);
  }

  private static Rectangle getPixelBounds(Font font, String text, FontRenderContext context) {
    return font.hasLayoutAttributes()
           ? new TextLayout(text, font, context).getPixelBounds(context, 0, 0)
           : font.createGlyphVector(context, text).getPixelBounds(context, 0, 0);
  }

  private static FontRenderContext createContext() {
    Object aaHint = UIManager.get(RenderingHints.KEY_TEXT_ANTIALIASING);
    if (aaHint == null) aaHint = RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT;
    Object fmHint = UIManager.get(RenderingHints.KEY_FRACTIONALMETRICS);
    if (fmHint == null) fmHint = RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT;
    return new FontRenderContext(null, aaHint, fmHint);
  }
}
