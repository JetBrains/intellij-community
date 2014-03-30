/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.fileEditor.impl;

import com.intellij.util.ui.UIUtil;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.util.Map;

/**
 * @author max
 */
public class WaverGraphicsDecorator extends Graphics2D {
  public static int WAVE_ALPHA_KEY = 0xFE;
  private final Graphics2D myOriginal;
  private final Color myWaveColor;

  public WaverGraphicsDecorator(final Graphics2D original, final Color waveColor) {
    myOriginal = original;
    myWaveColor = waveColor;
  }

  private void drawWave(String text, int offset, int baseline) {
    Color fore = getColor();
    if (fore.getAlpha() == WAVE_ALPHA_KEY) {
      int width = getFontMetrics().stringWidth(text);
      setColor(myWaveColor);
      final int wavedAt = baseline + 1;
      for (int x = offset; x < offset + width; x += 4) {
        UIUtil.drawLine(this, x, wavedAt, x + 2, wavedAt + 2);
        UIUtil.drawLine(this, x + 3, wavedAt + 1, x + 4, wavedAt);
      }
      setColor(fore);
    }
  }

  @Override
  public void draw(final Shape s) {
    myOriginal.draw(s);
  }

  @Override
  public boolean drawImage(final Image img, final AffineTransform xform, final ImageObserver obs) {
    return myOriginal.drawImage(img, xform, obs);
  }

  @Override
  public void drawImage(final BufferedImage img, final BufferedImageOp op, final int x, final int y) {
    myOriginal.drawImage(img, op, x, y);
  }

  @Override
  public void drawRenderedImage(final RenderedImage img, final AffineTransform xform) {
    myOriginal.drawRenderedImage(img, xform);
  }

  @Override
  public void drawRenderableImage(final RenderableImage img, final AffineTransform xform) {
    myOriginal.drawRenderableImage(img, xform);
  }

  @Override
  public void drawString(final String str, final int x, final int y) {
    myOriginal.drawString(str, x, y);
    drawWave(str, x, y);
  }

  @Override
  public void drawString(final String s, final float x, final float y) {
    myOriginal.drawString(s, x, y);
    drawWave(s, (int)x, (int)y);
  }

  @Override
  public void drawString(final AttributedCharacterIterator iterator, final int x, final int y) {
    myOriginal.drawString(iterator, x, y);
    //TODO: drawWave
  }

  @Override
  public void drawString(final AttributedCharacterIterator iterator, final float x, final float y) {
    myOriginal.drawString(iterator, x, y);
    //TODO: drawWave
  }

  @Override
  public void drawGlyphVector(final GlyphVector g, final float x, final float y) {
    myOriginal.drawGlyphVector(g, x, y);
    //TODO: drawWave
  }

  @Override
  public void fill(final Shape s) {
    myOriginal.fill(s);
  }

  @Override
  public boolean hit(final Rectangle rect, final Shape s, final boolean onStroke) {
    return myOriginal.hit(rect, s, onStroke);
  }

  @Override
  public GraphicsConfiguration getDeviceConfiguration() {
    return myOriginal.getDeviceConfiguration();
  }

  @Override
  public void setComposite(final Composite comp) {
    myOriginal.setComposite(comp);
  }

  @Override
  public void setPaint(final Paint paint) {
    myOriginal.setPaint(paint);
  }

  @Override
  public void setStroke(final Stroke s) {
    myOriginal.setStroke(s);
  }

  @Override
  public void setRenderingHint(final RenderingHints.Key hintKey, final Object hintValue) {
    myOriginal.setRenderingHint(hintKey, hintValue);
  }

  @Override
  public Object getRenderingHint(final RenderingHints.Key hintKey) {
    return myOriginal.getRenderingHint(hintKey);
  }

  @Override
  public void setRenderingHints(final Map<?, ?> hints) {
    myOriginal.setRenderingHints(hints);
  }

  @Override
  public void addRenderingHints(final Map<?, ?> hints) {
    myOriginal.addRenderingHints(hints);
  }

  @Override
  public RenderingHints getRenderingHints() {
    return myOriginal.getRenderingHints();
  }

  @Override
  public void translate(final int x, final int y) {
    myOriginal.translate(x, y);
  }

  @Override
  public void translate(final double tx, final double ty) {
    myOriginal.translate(tx, ty);
  }

  @Override
  public void rotate(final double theta) {
    myOriginal.rotate(theta);
  }

  @Override
  public void rotate(final double theta, final double x, final double y) {
    myOriginal.rotate(theta, x, y);
  }

  @Override
  public void scale(final double sx, final double sy) {
    myOriginal.scale(sx, sy);
  }

  @Override
  public void shear(final double shx, final double shy) {
    myOriginal.shear(shx, shy);
  }

  @Override
  public void transform(final AffineTransform Tx) {
    myOriginal.transform(Tx);
  }

  @Override
  public void setTransform(final AffineTransform Tx) {
    myOriginal.setTransform(Tx);
  }

  @Override
  public AffineTransform getTransform() {
    return myOriginal.getTransform();
  }

  @Override
  public Paint getPaint() {
    return myOriginal.getPaint();
  }

  @Override
  public Composite getComposite() {
    return myOriginal.getComposite();
  }

  @Override
  public void setBackground(final Color color) {
    myOriginal.setBackground(color);
  }

  @Override
  public Color getBackground() {
    return myOriginal.getBackground();
  }

  @Override
  public Stroke getStroke() {
    return myOriginal.getStroke();
  }

  @Override
  public void clip(final Shape s) {
    myOriginal.clip(s);
  }

  @Override
  public FontRenderContext getFontRenderContext() {
    return myOriginal.getFontRenderContext();
  }

  @Override
  public Graphics create() {
    return new WaverGraphicsDecorator((Graphics2D)myOriginal.create(), myWaveColor);
  }

  @Override
  public Color getColor() {
    return myOriginal.getColor();
  }

  @Override
  public void setColor(final Color c) {
    myOriginal.setColor(c);
  }

  @Override
  public void setPaintMode() {
    myOriginal.setPaintMode();
  }

  @Override
  public void setXORMode(final Color c1) {
    myOriginal.setXORMode(c1);
  }

  @Override
  public Font getFont() {
    return myOriginal.getFont();
  }

  @Override
  public void setFont(final Font font) {
    myOriginal.setFont(font);
  }

  @Override
  public FontMetrics getFontMetrics(final Font f) {
    return myOriginal.getFontMetrics(f);
  }

  @Override
  public Rectangle getClipBounds() {
    return myOriginal.getClipBounds();
  }

  @Override
  public void clipRect(final int x, final int y, final int width, final int height) {
    myOriginal.clipRect(x, y, width, height);
  }

  @Override
  public void setClip(final int x, final int y, final int width, final int height) {
    myOriginal.setClip(x, y, width, height);
  }

  @Override
  public Shape getClip() {
    return myOriginal.getClip();
  }

  @Override
  public void setClip(final Shape clip) {
    myOriginal.setClip(clip);
  }

  @Override
  public void copyArea(final int x, final int y, final int width, final int height, final int dx, final int dy) {
    myOriginal.copyArea(x, y, width, height, dx, dy);
  }

  @Override
  public void drawLine(final int x1, final int y1, final int x2, final int y2) {
    myOriginal.drawLine(x1, y1, x2, y2);
  }

  @Override
  public void fillRect(final int x, final int y, final int width, final int height) {
    myOriginal.fillRect(x, y, width, height);
  }

  @Override
  public void clearRect(final int x, final int y, final int width, final int height) {
    myOriginal.clearRect(x, y, width, height);
  }

  @Override
  public void drawRoundRect(final int x, final int y, final int width, final int height, final int arcWidth, final int arcHeight) {
    myOriginal.drawRoundRect(x, y, width, height, arcWidth, arcHeight);
  }

  @Override
  public void fillRoundRect(final int x, final int y, final int width, final int height, final int arcWidth, final int arcHeight) {
    myOriginal.fillRoundRect(x, y, width, height, arcWidth, arcHeight);
  }

  @Override
  public void drawOval(final int x, final int y, final int width, final int height) {
    myOriginal.drawOval(x, y, width, height);
  }

  @Override
  public void fillOval(final int x, final int y, final int width, final int height) {
    myOriginal.fillOval(x, y, width, height);
  }

  @Override
  public void drawArc(final int x, final int y, final int width, final int height, final int startAngle, final int arcAngle) {
    myOriginal.drawArc(x, y, width, height, startAngle, arcAngle);
  }

  @Override
  public void fillArc(final int x, final int y, final int width, final int height, final int startAngle, final int arcAngle) {
    myOriginal.fillArc(x, y, width, height, startAngle, arcAngle);
  }

  @Override
  public void drawPolyline(final int[] xPoints, final int[] yPoints, final int nPoints) {
    myOriginal.drawPolyline(xPoints, yPoints, nPoints);
  }

  @Override
  public void drawPolygon(final int[] xPoints, final int[] yPoints, final int nPoints) {
    myOriginal.drawPolygon(xPoints, yPoints, nPoints);
  }

  @Override
  public void fillPolygon(final int[] xPoints, final int[] yPoints, final int nPoints) {
    myOriginal.fillPolygon(xPoints, yPoints, nPoints);
  }

  @Override
  public boolean drawImage(final Image img, final int x, final int y, final ImageObserver observer) {
    return myOriginal.drawImage(img, x, y, observer);
  }

  @Override
  public boolean drawImage(final Image img, final int x, final int y, final int width, final int height, final ImageObserver observer) {
    return myOriginal.drawImage(img, x, y, width, height, observer);
  }

  @Override
  public boolean drawImage(final Image img, final int x, final int y, final Color bgcolor, final ImageObserver observer) {
    return myOriginal.drawImage(img, x, y, bgcolor, observer);
  }

  @Override
  public boolean drawImage(final Image img,
                           final int x,
                           final int y,
                           final int width,
                           final int height,
                           final Color bgcolor,
                           final ImageObserver observer) {
    return myOriginal.drawImage(img, x, y, width, height, bgcolor, observer);
  }

  @Override
  public boolean drawImage(final Image img,
                           final int dx1,
                           final int dy1,
                           final int dx2,
                           final int dy2,
                           final int sx1,
                           final int sy1,
                           final int sx2,
                           final int sy2,
                           final ImageObserver observer) {
    return myOriginal.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer);
  }

  @Override
  public boolean drawImage(final Image img,
                           final int dx1,
                           final int dy1,
                           final int dx2,
                           final int dy2,
                           final int sx1,
                           final int sy1,
                           final int sx2,
                           final int sy2,
                           final Color bgcolor,
                           final ImageObserver observer) {
    return myOriginal.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, bgcolor, observer);
  }

  @Override
  public void dispose() {
    //myOriginal.dispose();
  }


  @Override
  public Rectangle getClipRect() {
    return myOriginal.getClipRect();
  }

  @Override
  public boolean hitClip(final int x, final int y, final int width, final int height) {
    return myOriginal.hitClip(x, y, width, height);
  }

  @Override
  public Rectangle getClipBounds(final Rectangle r) {
    return myOriginal.getClipBounds(r);
  }

  @Override
  public void fill3DRect(final int x, final int y, final int width, final int height, final boolean raised) {
    myOriginal.fill3DRect(x, y, width, height, raised);
  }

  @Override
  public void draw3DRect(final int x, final int y, final int width, final int height, final boolean raised) {
    myOriginal.draw3DRect(x, y, width, height, raised);
  }

  @Override
  public Graphics create(final int x, final int y, final int width, final int height) {
    return new WaverGraphicsDecorator((Graphics2D)myOriginal.create(x, y, width, height), myWaveColor);
  }

  @Override
  public void drawRect(final int x, final int y, final int width, final int height) {
    myOriginal.drawRect(x, y, width, height);
  }

  @Override
  public void drawPolygon(final Polygon p) {
    myOriginal.drawPolygon(p);
  }

  @Override
  public void fillPolygon(final Polygon p) {
    myOriginal.fillPolygon(p);
  }

  @Override
  public FontMetrics getFontMetrics() {
    return myOriginal.getFontMetrics();
  }
}
