/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.editor;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.util.Map;
import java.util.Objects;

/**
 * For use in painting tests. To make sure drawing method calls are not optimized away on execution, they all change an internal state,
 * which should be retrieved on painting finish using {@link #getResult()} method.
 */
public class NullGraphics2D extends Graphics2D {
  private final FontRenderContext myFontRenderContext = new FontRenderContext(null, false, false);
  private Rectangle myClip;
  private Composite myComposite = AlphaComposite.SrcOver;
  private RenderingHints myRenderingHints = new RenderingHints(null);
  private Color myColor = Color.black;
  private Font myFont = Font.decode(null);
  private Stroke myStroke = new BasicStroke();

  private int myResult;

  public NullGraphics2D(Rectangle clip) {
    myClip = clip;
  }
  
  public int getResult() {
    return myResult;
  }

  @Override
  public void draw(Shape s) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void drawRenderedImage(RenderedImage img, AffineTransform xform) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void drawRenderableImage(RenderableImage img, AffineTransform xform) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void drawString(String str, int x, int y) {
    myResult += x;
    myResult += y;
    for (int i = 0; i < str.length(); i++) {
      myResult += str.charAt(i);
    }
  }

  @Override
  public void drawString(String str, float x, float y) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void drawString(AttributedCharacterIterator iterator, int x, int y) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean drawImage(Image img, int x, int y, Color bgcolor, ImageObserver observer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean drawImage(Image img, int x, int y, int width, int height, Color bgcolor, ImageObserver observer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, ImageObserver observer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, Color bgcolor,
                           ImageObserver observer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void dispose() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void drawString(AttributedCharacterIterator iterator, float x, float y) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void drawGlyphVector(GlyphVector g, float x, float y) {
    myResult += x;
    myResult += y;
    for (int i = 0; i < g.getNumGlyphs(); i++) {
      myResult += g.getGlyphCode(i);
      Point2D position = g.getGlyphPosition(i);
      myResult += position.getX();
      myResult += position.getY();
    }
  }

  @Override
  public void fill(Shape s) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hit(Rectangle rect, Shape s, boolean onStroke) {
    throw new UnsupportedOperationException();
  }

  @Override
  public GraphicsConfiguration getDeviceConfiguration() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setComposite(Composite comp) {
    myComposite = comp;
    myResult += Objects.hashCode(comp);
  }

  @Override
  public void setPaint(Paint paint) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setRenderingHint(RenderingHints.Key hintKey, Object hintValue) {
    myRenderingHints.put(hintKey, hintValue);
    myResult += Objects.hashCode(hintKey);
    myResult += Objects.hashCode(hintValue);
  }

  @Override
  public Object getRenderingHint(RenderingHints.Key hintKey) {
    return myRenderingHints.get(hintKey);
  }

  @Override
  public void setRenderingHints(Map<?, ?> hints) {
    myRenderingHints.clear();
    addRenderingHints(hints);
    myResult += Objects.hashCode(hints);
  }

  @Override
  public void addRenderingHints(Map<?, ?> hints) {
    for (Map.Entry<?, ?> entry : hints.entrySet()) {
      myRenderingHints.put(entry.getKey(), entry.getValue());
    }
    myResult += Objects.hashCode(hints);
  }

  @Override
  public RenderingHints getRenderingHints() {
    return (RenderingHints)myRenderingHints.clone();
  }

  @Override
  public Graphics create() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void translate(int x, int y) {
    myResult += x;
    myResult += y;
  }

  @Override
  public Color getColor() {
    return myColor;
  }

  @Override
  public void setColor(Color c) {
    myColor = c;
    myResult += Objects.hashCode(c);
  }

  @Override
  public void setPaintMode() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setXORMode(Color c1) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Font getFont() {
    return myFont;
  }

  @Override
  public void setFont(Font font) {
    myFont = font;
    myResult += Objects.hashCode(font);
  }

  @Override
  public void setStroke(Stroke s) {
    myStroke = s;
    myResult += Objects.hashCode(s);
  }

  @Override
  public Stroke getStroke() {
    return myStroke;
  }

  @Override
  public FontMetrics getFontMetrics(Font f) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Rectangle getClipBounds() {
    return myClip == null ? null : new Rectangle(myClip);
  }

  @Override
  public void clipRect(int x, int y, int width, int height) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setClip(int x, int y, int width, int height) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Shape getClip() {
    return myClip == null ? null : new Rectangle(myClip);
  }

  @Override
  public void setClip(Shape clip) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void copyArea(int x, int y, int width, int height, int dx, int dy) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void drawLine(int x1, int y1, int x2, int y2) {
    myResult += x1;
    myResult += x2;
    myResult += y1;
    myResult += y2;
  }
  
  @Override
  public void fillRect(int x, int y, int width, int height) {
    myResult += x;
    myResult += y;
    myResult += width;
    myResult += height;
  }

  @Override
  public void clearRect(int x, int y, int width, int height) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void drawOval(int x, int y, int width, int height) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void fillOval(int x, int y, int width, int height) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void translate(double tx, double ty) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void rotate(double theta) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void rotate(double theta, double x, double y) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void scale(double sx, double sy) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void shear(double shx, double shy) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void transform(AffineTransform Tx) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setTransform(AffineTransform Tx) {
    throw new UnsupportedOperationException();
  }

  @Override
  public AffineTransform getTransform() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Paint getPaint() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Composite getComposite() {
    return myComposite;
  }

  @Override
  public void setBackground(Color color) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Color getBackground() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clip(Shape s) {
    throw new UnsupportedOperationException();
  }

  @Override
  public FontRenderContext getFontRenderContext() {
    return myFontRenderContext;
  }
}
