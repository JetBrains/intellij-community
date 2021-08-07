// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

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
 * Wrap a graphics2d objects to debug internals paintings
 *
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("UnusedDeclaration")
public class Graphics2DLog extends Graphics2D {
  protected final Graphics2D myPeer;

  public Graphics2DLog(Graphics g) {
    myPeer = (Graphics2D)g;
  }

  @SuppressWarnings({"UseOfSystemOutOrSystemErr"})
  protected void log(@NonNls String msg) {
    System.out.println(msg);
  }

  @Override
  public void draw3DRect(int x, int y, int width, int height, boolean raised) {
    log(String.format("draw3DRect(%d, %d, %d, %d, %b)", x, y, width, height, raised));
    myPeer.draw3DRect(x, y, width, height, raised);
  }

  @Override
  public void fill3DRect(int x, int y, int width, int height, boolean raised) {
    log(String.format("fill3DRect(%d, %d, %d, %d, %b)", x, y, width, height, raised));
    myPeer.fill3DRect(x, y, width, height, raised);
  }

  @Override
  public void draw(Shape s) {
    log("draw(" + s + ")");
    myPeer.draw(s);
  }

  @Override
  public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs) {
    log("drawImage(Image, AffineTransform, ImageObserver)");
    return myPeer.drawImage(img, xform, obs);
  }

  @Override
  public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) {
    log(String.format("drawImage(BufferedImage, BufferedImageOp, %d, %d)", x, y));
    myPeer.drawImage(img, op, x, y);
  }

  @Override
  public void drawRenderedImage(RenderedImage img, AffineTransform xform) {
    log("drawRenderedImage(RenderedImage, AffineTransform)");
    myPeer.drawRenderedImage(img, xform);
  }

  @Override
  public void drawRenderableImage(RenderableImage img, AffineTransform xform) {
    log("drawRenderableImage(RenderableImage, AffineTransform)");
    myPeer.drawRenderableImage(img, xform);
  }

  @Override
  public void drawString(String str, int x, int y) {
    log(String.format("drawString(%s, %d, %d)", str, x, y));
    myPeer.drawString(str, x, y);
  }

  @Override
  public void drawString(String str, float x, float y) {
    log(String.format("drawString(%s, %f, %f)", str, x, y));
    myPeer.drawString(str, x, y);
  }

  @Override
  public void drawString(AttributedCharacterIterator iterator, int x, int y) {
    log(String.format("drawString(%s, %d, %d)", iterator, x, y));
    myPeer.drawString(iterator, x, y);
  }

  @Override
  public void drawString(AttributedCharacterIterator iterator, float x, float y) {
    log(String.format("drawString(%s, %f, %f)", iterator, x, y));
    myPeer.drawString(iterator, x, y);
  }

  @Override
  public void drawGlyphVector(GlyphVector g, float x, float y) {
    log(String.format("drawGlyphVector(%s, %f, %f)", g, x, y));
    myPeer.drawGlyphVector(g, x, y);
  }

  @Override
  public void fill(Shape s) {
    log(String.format("fill(%s)", s));
    myPeer.fill(s);
  }

  @Override
  public boolean hit(Rectangle rect, Shape s, boolean onStroke) {
    log(String.format("hit(%s, %s, %s)", rect, s, onStroke));
    return myPeer.hit(rect, s, onStroke);
  }

  @Override
  public GraphicsConfiguration getDeviceConfiguration() {
    return myPeer.getDeviceConfiguration();
  }

  @Override
  public void setComposite(Composite comp) {
    log(String.format("setComposite(%s)", comp));
    myPeer.setComposite(comp);
  }

  @Override
  public void setPaint(Paint paint) {
    log(String.format("setPaint(%s)", paint));
    myPeer.setPaint(paint);
  }

  @Override
  public void setStroke(Stroke s) {
    log(String.format("setStroke(%s)", s));
    myPeer.setStroke(s);
  }

  @Override
  public void setRenderingHint(RenderingHints.Key hintKey, Object hintValue) {
    log(String.format("setRenderingHint(%s, %s)", hintKey, hintValue));
    myPeer.setRenderingHint(hintKey, hintValue);
  }

  @Override
  public Object getRenderingHint(RenderingHints.Key hintKey) {
    log(String.format("getRenderingHint(%s)", hintKey));
    return myPeer.getRenderingHint(hintKey);
  }

  @Override
  public void setRenderingHints(Map<?, ?> hints) {
    log(String.format("setRenderingHints(%s)", hints));
    myPeer.setRenderingHints(hints);
  }

  @Override
  public void addRenderingHints(Map<?, ?> hints) {
    log(String.format("addRenderingHints(%s)", hints));
    myPeer.addRenderingHints(hints);
  }

  @Override
  public RenderingHints getRenderingHints() {
    log("getRenderingHints()");
    return myPeer.getRenderingHints();
  }

  @Override
  public void translate(int x, int y) {
    log(String.format("translate(%d, %d)", x, y));
    myPeer.translate(x, y);
  }

  @Override
  public void translate(double tx, double ty) {
    log(String.format("translate(%f, %f)", tx, ty));
    myPeer.translate(tx, ty);
  }

  @Override
  public void rotate(double theta) {
    log(String.format("rotate(%f)", theta));
    myPeer.rotate(theta);
  }

  @Override
  public void rotate(double theta, double x, double y) {
    log(String.format("rotate(%f, %f, %f)", theta, x, y));
    myPeer.rotate(theta, x, y);
  }

  @Override
  public void scale(double sx, double sy) {
    log(String.format("scale(%f, %f)", sx, sy));
    myPeer.scale(sx, sy);
  }

  @Override
  public void shear(double shx, double shy) {
    log(String.format("shear(%f, %f)", shx, shy));
    myPeer.shear(shx, shy);
  }

  @Override
  public void transform(AffineTransform Tx) {
    log(String.format("transform(%s)", Tx));
    myPeer.transform(Tx);
  }

  @Override
  public void setTransform(AffineTransform Tx) {
    log(String.format("setTransform(%s)", Tx));
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
    log(String.format("setBackground(%s)", toHex(color)));
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
    log(String.format("clip(%s)", s));
    myPeer.clip(s);
  }

  @Override
  public FontRenderContext getFontRenderContext() {
    return myPeer.getFontRenderContext();
  }

  @Override
  public Graphics create() {
    return new Graphics2DLog(myPeer.create());
  }

  @Override
  public Graphics create(int x, int y, int width, int height) {
    log(String.format("create(%d, %d %d, %d)", x, y, width, height));
    return new Graphics2DLog(myPeer.create(x, y, width, height));
  }

  @Override
  public Color getColor() {
    return myPeer.getColor();
  }

  @Override
  public void setColor(Color c) {
    log(String.format("setColor(%s) alpha=%d", toHex(c), c == null ? 0 : c.getAlpha()));
    myPeer.setColor(c);
  }

  @Override
  public void setPaintMode() {
    log("setPaintMode()");
    myPeer.setPaintMode();
  }

  @Override
  public void setXORMode(Color c1) {
    log(String.format("setXORMode(%s)", toHex(c1)));
    myPeer.setXORMode(c1);
  }

  @Override
  public Font getFont() {
    return myPeer.getFont();
  }

  @Override
  public void setFont(Font font) {
    log(String.format("setFont(%s)", font));
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
    log(String.format("clipRect(%d, %d, %d, %d)", x, y, width, height));
    myPeer.clipRect(x, y, width, height);
  }

  @Override
  public void setClip(int x, int y, int width, int height) {
    log(String.format("setClip(%d, %d, %d, %d)", x, y, width, height));
    myPeer.setClip(x, y, width, height);
  }

  @Override
  public Shape getClip() {
    log("getClip()");
    return myPeer.getClip();
  }

  @Override
  public void setClip(Shape clip) {
    log(String.format("setClip(%s)", clip));
    myPeer.setClip(clip);
  }

  @Override
  public void copyArea(int x, int y, int width, int height, int dx, int dy) {
    log(String.format("copyArea(%d, %d, %d, %d, %d, %d)", x, y, width, height, dx, dy));
    myPeer.copyArea(x, y, width, height, dx, dy);
  }

  @Override
  public void drawLine(int x1, int y1, int x2, int y2) {
    log(String.format("drawLine(%d, %d, %d, %d)", x1, y1, x2, y2));
    myPeer.drawLine(x1, y1, x2, y2);
  }

  @Override
  public void fillRect(int x, int y, int width, int height) {
    log(String.format("fillRect(%d, %d, %d, %d)", x, y, width, height));
    myPeer.fillRect(x, y, width, height);
  }

  @Override
  public void drawRect(int x, int y, int width, int height) {
    log(String.format("drawRect(%d, %d, %d, %d)", x, y, width, height));
    myPeer.drawRect(x, y, width, height);
  }

  @Override
  public void clearRect(int x, int y, int width, int height) {
    log(String.format("clearRect(%d, %d, %d, %d)", x, y, width, height));
    myPeer.clearRect(x, y, width, height);
  }

  @Override
  public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
    log(String.format("drawRoundRect(%d, %d, %d, %d, %d, %d)", x, y, width, height, arcWidth, arcHeight));
    myPeer.drawRoundRect(x, y, width, height, arcWidth, arcHeight);
  }

  @Override
  public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
    log(String.format("fillRoundRect(%d, %d, %d, %d, %d, %d)", x, y, width, height, arcWidth, arcHeight));
    myPeer.fillRoundRect(x, y, width, height, arcWidth, arcHeight);
  }

  @Override
  public void drawOval(int x, int y, int width, int height) {
    log(String.format("drawOval(%d, %d, %d, %d)", x, y, width, height));
    myPeer.drawOval(x, y, width, height);
  }

  @Override
  public void fillOval(int x, int y, int width, int height) {
    log(String.format("fillOval(%d, %d, %d, %d)", x, y, width, height));
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
    log("drawPolyline(int[], int[], int)");
    myPeer.drawPolyline(xPoints, yPoints, nPoints);
  }

  @Override
  public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
    log("drawPolygon(int[], int[], int)");
    myPeer.drawPolygon(xPoints, yPoints, nPoints);
  }

  @Override
  public void drawPolygon(Polygon p) {
    log("drawPolygon()");
    myPeer.drawPolygon(p);
  }

  @Override
  public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
    log("fillPolygon(int[], int[], int)");
    myPeer.fillPolygon(xPoints, yPoints, nPoints);
  }

  @Override
  public void fillPolygon(Polygon p) {
    log("fillPolygon(" + p + ")");
    myPeer.fillPolygon(p);
  }

  @Override
  public void drawChars(char[] data, int offset, int length, int x, int y) {
    log("drawChars()");
    myPeer.drawChars(data, offset, length, x, y);
  }

  @Override
  public void drawBytes(byte[] data, int offset, int length, int x, int y) {
    log("drawBytes");
    myPeer.drawBytes(data, offset, length, x, y);
  }

  @Override
  public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
    if(img == null) {
      log(String.format("drawImage(Image(null), %d, %d, ImageObserver)", x, y));
    } else {
      log(String.format("drawImage(Image(%d,%d), %d, %d, ImageObserver)", img.getWidth(null), img.getHeight(null), x, y));
    }
    return myPeer.drawImage(img, x, y, observer);
  }

  @Override
  public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer) {
    log("drawImage(Image,int,int,int,int,ImageObserver)");
    return myPeer.drawImage(img, x, y, width, height, observer);
  }

  @Override
  public boolean drawImage(Image img, int x, int y, Color bgcolor, ImageObserver observer) {
    log("drawImage(Image,int,int,Color,ImageObserver)");
    return myPeer.drawImage(img, x, y, bgcolor, observer);
  }

  @Override
  public boolean drawImage(Image img, int x, int y, int width, int height, Color bgcolor, ImageObserver observer) {
    log("drawImage(Image,int,int,int,int,Color,ImageObserver)");
    return myPeer.drawImage(img, x, y, width, height, bgcolor, observer);
  }

  @Override
  public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, ImageObserver observer) {
    log("drawImage(Image,int,int,int,int,int,int,int,int,ImageObserver)");
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
    log("drawImage(Image,int,int,int,int,int,int,int,int,Color,ImageObserver)");
    return myPeer.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, bgcolor, observer);
  }

  @Override
  public void dispose() {
    log("dispose()");
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
    log(String.format("hitClip(%d, %d, %d, %d)", x, y, width, height));
    return myPeer.hitClip(x, y, width, height);
  }

  @Override
  public Rectangle getClipBounds(Rectangle r) {
    return myPeer.getClipBounds(r);
  }

  @Nullable
  private static String toHex(Color c) {
    return c == null ? null : ColorUtil.toHex(c);
  }
}
