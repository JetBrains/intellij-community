/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.util.ui.GraphicsUtil;

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
 * @author Konstantin Bulenkov
 */
class HiDPIScaledGraphics extends Graphics2D {
  protected final Graphics2D myPeer;
  private BufferedImage myImage;

  public HiDPIScaledGraphics(Graphics g, BufferedImage image) {
    myImage = image;
    myPeer = (Graphics2D)g;
    scale(2, 2);
    GraphicsUtil.setupAAPainting(myPeer);
  }

  @Override
  public void draw3DRect(int x, int y, int width, int height, boolean raised) {
    myPeer.draw3DRect(x, y, width, height, raised);
  }

  @Override
  public void fill3DRect(int x, int y, int width, int height, boolean raised) {
    myPeer.fill3DRect(x, y, width, height, raised);
  }

  @Override
  public void draw(Shape s) {
    myPeer.draw(s);
  }

  @Override
  public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs) {
    return myPeer.drawImage(img, xform, obs);
  }

  @Override
  public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) {
    myPeer.drawImage(img, op, x, y);
  }

  @Override
  public void drawRenderedImage(RenderedImage img, AffineTransform xform) {
    myPeer.drawRenderedImage(img, xform);
  }

  @Override
  public void drawRenderableImage(RenderableImage img, AffineTransform xform) {
    myPeer.drawRenderableImage(img, xform);
  }

  @Override
  public void drawString(String str, int x, int y) {
    myPeer.drawString(str, x, y);
  }

  @Override
  public void drawString(String str, float x, float y) {
    myPeer.drawString(str, x, y);
  }

  @Override
  public void drawString(AttributedCharacterIterator iterator, int x, int y) {
    myPeer.drawString(iterator, x, y);
  }

  @Override
  public void drawString(AttributedCharacterIterator iterator, float x, float y) {
    myPeer.drawString(iterator, x, y);
  }

  @Override
  public void drawGlyphVector(GlyphVector g, float x, float y) {
    myPeer.drawGlyphVector(g, x, y);
  }

  @Override
  public void fill(Shape s) {
    myPeer.fill(s);
  }

  @Override
  public boolean hit(Rectangle rect, Shape s, boolean onStroke) {
    return myPeer.hit(rect, s, onStroke);
  }

  @Override
  public GraphicsConfiguration getDeviceConfiguration() {
    return myPeer.getDeviceConfiguration();
  }

  @Override
  public void setComposite(Composite comp) {
    myPeer.setComposite(comp);
  }

  @Override
  public void setPaint(Paint paint) {
    myPeer.setPaint(paint);
  }

  @Override
  public void setStroke(Stroke s) {
    myPeer.setStroke(s);
  }

  @Override
  public void setRenderingHint(RenderingHints.Key hintKey, Object hintValue) {
    myPeer.setRenderingHint(hintKey, hintValue);
  }

  @Override
  public Object getRenderingHint(RenderingHints.Key hintKey) {
    return myPeer.getRenderingHint(hintKey);
  }

  @Override
  public void setRenderingHints(Map<?, ?> hints) {
    myPeer.setRenderingHints(hints);
  }

  @Override
  public void addRenderingHints(Map<?, ?> hints) {
    myPeer.addRenderingHints(hints);
  }

  @Override
  public RenderingHints getRenderingHints() {
    return myPeer.getRenderingHints();
  }

  @Override
  public void translate(int x, int y) {
    myPeer.translate(x, y);
  }

  @Override
  public void translate(double tx, double ty) {
    myPeer.translate(tx, ty);
  }

  @Override
  public void rotate(double theta) {
    myPeer.rotate(theta);
  }

  @Override
  public void rotate(double theta, double x, double y) {
    myPeer.rotate(theta, x, y);
  }

  @Override
  public void scale(double sx, double sy) {
    myPeer.scale(sx, sy);
  }

  @Override
  public void shear(double shx, double shy) {
    myPeer.shear(shx, shy);
  }

  @Override
  public void transform(AffineTransform Tx) {
    myPeer.transform(Tx);
  }

  @Override
  public void setTransform(AffineTransform Tx) {
    myPeer.setTransform(Tx);
  }

  @Override
  public AffineTransform getTransform() {
    return myPeer.getTransform();
  }

  @Override
  public Paint getPaint() {
    return myPeer.getPaint();
  }

  @Override
  public Composite getComposite() {
    return myPeer.getComposite();
  }

  @Override
  public void setBackground(Color color) {
    myPeer.setBackground(color);
  }

  @Override
  public Color getBackground() {
    return myPeer.getBackground();
  }

  @Override
  public Stroke getStroke() {
    return myPeer.getStroke();
  }

  @Override
  public void clip(Shape s) {
    myPeer.clip(s);
  }

  @Override
  public FontRenderContext getFontRenderContext() {
    return myPeer.getFontRenderContext();
  }

  @Override
  public Graphics create() {
    Graphics g = myPeer.create();
    return g;
  }

  @Override
  public Graphics create(int x, int y, int width, int height) {
    return myPeer.create(x, y, width, height);
  }

  @Override
  public Color getColor() {
    return myPeer.getColor();
  }

  @Override
  public void setColor(Color c) {
    myPeer.setColor(c);
  }

  @Override
  public void setPaintMode() {
    myPeer.setPaintMode();
  }

  @Override
  public void setXORMode(Color c1) {
    myPeer.setXORMode(c1);
  }

  @Override
  public Font getFont() {
    return myPeer.getFont();
  }

  @Override
  public void setFont(Font font) {
    myPeer.setFont(font);
  }

  @Override
  public FontMetrics getFontMetrics() {
    return myPeer.getFontMetrics();
  }

  @Override
  public FontMetrics getFontMetrics(Font f) {
    return myPeer.getFontMetrics(f);
  }

  @Override
  public Rectangle getClipBounds() {
    return myPeer.getClipBounds();
  }

  @Override
  public void clipRect(int x, int y, int width, int height) {
    myPeer.clipRect(x, y, width, height);
  }

  @Override
  public void setClip(int x, int y, int width, int height) {
    myPeer.setClip(x, y, width, height);
  }

  @Override
  public Shape getClip() {
    return myPeer.getClip();
  }

  @Override
  public void setClip(Shape clip) {
    myPeer.setClip(clip);
  }

  @Override
  public void copyArea(int x, int y, int width, int height, int dx, int dy) {
    myPeer.copyArea(x, y, width, height, dx, dy);
  }

  @Override
  public void drawLine(int x1, int y1, int x2, int y2) {
    myPeer.drawLine(x1, y1, x2, y2);
  }

  @Override
  public void fillRect(int x, int y, int width, int height) {
    myPeer.fillRect(x, y, width, height);
  }

  @Override
  public void drawRect(int x, int y, int width, int height) {
    myPeer.drawRect(x, y, width, height);
  }

  @Override
  public void clearRect(int x, int y, int width, int height) {
    myPeer.clearRect(x, y, width, height);
  }

  @Override
  public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
    myPeer.drawRoundRect(x, y, width, height, arcWidth, arcHeight);
  }

  @Override
  public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
    myPeer.fillRoundRect(x, y, width, height, arcWidth, arcHeight);
  }

  @Override
  public void drawOval(int x, int y, int width, int height) {
    myPeer.drawOval(x, y, width, height);
  }

  @Override
  public void fillOval(int x, int y, int width, int height) {
    myPeer.fillOval(x, y, width, height);
  }

  @Override
  public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
    myPeer.drawArc(x, y, width, height, startAngle, arcAngle);
  }

  @Override
  public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
    myPeer.fillArc(x, y, width, height, startAngle, arcAngle);
  }

  @Override
  public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {

    myPeer.drawPolyline(xPoints, yPoints, nPoints);
  }

  @Override
  public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {

    myPeer.drawPolygon(xPoints, yPoints, nPoints);
  }

  @Override
  public void drawPolygon(Polygon p) {

    myPeer.drawPolygon(p);
  }

  @Override
  public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {

    myPeer.fillPolygon(xPoints, yPoints, nPoints);
  }

  @Override
  public void fillPolygon(Polygon p) {

    myPeer.fillPolygon(p);
  }

  @Override
  public void drawChars(char[] data, int offset, int length, int x, int y) {
    myPeer.drawChars(data, offset, length, x, y);
  }

  @Override
  public void drawBytes(byte[] data, int offset, int length, int x, int y) {
    myPeer.drawBytes(data, offset, length, x, y);
  }

  @Override
  public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
    return myPeer.drawImage(img, x, y, observer);
  }

  @Override
  public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer) {
    return myPeer.drawImage(img, x, y, width, height, observer);
  }

  @Override
  public boolean drawImage(Image img, int x, int y, Color bgcolor, ImageObserver observer) {
    return myPeer.drawImage(img, x, y, bgcolor, observer);
  }

  @Override
  public boolean drawImage(Image img, int x, int y, int width, int height, Color bgcolor, ImageObserver observer) {
    return myPeer.drawImage(img, x, y, width, height, bgcolor, observer);
  }

  @Override
  public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, ImageObserver observer) {
    return myPeer.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer);
  }

  @Override
  public boolean drawImage(Image img,
                           int dx1,
                           int dy1,
                           int dx2,
                           int dy2,
                           int sx1,
                           int sy1,
                           int sx2,
                           int sy2,
                           Color bgcolor,
                           ImageObserver observer) {
    return myPeer.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, bgcolor, observer);
  }

  @Override
  public void dispose() {
    myPeer.dispose();
  }

  @Override
  public String toString() {
    return myPeer.toString();
  }

  @Override
  @Deprecated
  public Rectangle getClipRect() {
    return myPeer.getClipRect();
  }

  @Override
  public boolean hitClip(int x, int y, int width, int height) {
    return myPeer.hitClip(x, y, width, height);
  }

  @Override
  public Rectangle getClipBounds(Rectangle r) {
    return myPeer.getClipBounds(r);
  }

}
