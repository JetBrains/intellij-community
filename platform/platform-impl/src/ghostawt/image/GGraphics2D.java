// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt.image;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import sun.awt.SunHints;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.RenderingHints.Key;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.AttributedCharacterIterator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;

/**
 * GGraphics2D for headless mode.
 * <p>
 * Parts are taken from:
 * https://github.com/frohoff/jdk8u-dev-jdk/blob/master/src/share/classes/sun/java2d/SunGraphics2D.java
 */

public class GGraphics2D extends Graphics2D {
  private GraphicsConfiguration _configuration;

  //    private BufferedImage buffer;


  public final static ArrayList<JsonElement> commands = new ArrayList<>();


  public GGraphics2D(GraphicsConfiguration configuration) {
    _configuration = configuration;

    //     buffer = ((GGraphicsConfiguration)configuration).buffer;
    clip.add(_configuration.getBounds());
    transform.add(new AffineTransform());
  }

  private static void addCommand(JsonObject obj) {
    synchronized (commands) {
      commands.add(obj);
    }
  }

  @Override
  public Graphics create() {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "create");
    addCommand(obj);


  //  clip.add(null);
    clip.add(_configuration.getBounds());
    transform.add(transform.isEmpty() ? new AffineTransform() : new AffineTransform(getTransform()));

    // return new GGraphics2D(_configuration);
    return this;
  }

  @Override
  public void draw(Shape s) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "draw");
    addCommand(obj);
  }

  private static JsonArray transformToJson(AffineTransform xform) {

    double[] flatmatrix = new double[6];
    xform.getMatrix(flatmatrix);

    JsonArray result = new JsonArray();
    for (int i = 0; i < flatmatrix.length; i++) {
      result.add(flatmatrix[i]);
    }

    return result;
  }

  @Override
  public void drawRenderedImage(RenderedImage img, AffineTransform xform) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "drawRenderedImage");
    addCommand(obj);
  }

  @Override
  public void drawRenderableImage(RenderableImage img, AffineTransform xform) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "drawRenderableImage");
    addCommand(obj);
  }

  @Override
  public void drawString(String str, int x, int y) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "drawString");
    obj.addProperty("str", str);
    obj.addProperty("x", x);
    obj.addProperty("y", y);
    addCommand(obj);
  }

  @Override
  public void drawString(String str, float x, float y) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "drawStringA");
    obj.addProperty("str", str);
    obj.addProperty("x", x);
    obj.addProperty("y", y);
    addCommand(obj);
  }

  @Override
  public void drawString(AttributedCharacterIterator iterator, int x, int y) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "drawStringB");
    addCommand(obj);
  }

  @Override
  public void drawString(AttributedCharacterIterator iterator, float x, float y) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "drawStringC");
    addCommand(obj);
  }

  @Override
  public void drawGlyphVector(GlyphVector g, float x, float y) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "drawGlyphVector");
    addCommand(obj);
  }

  @Override
  public void fill(Shape s) {

    if (s instanceof Rectangle) {
      Rectangle rect = (Rectangle)s;
      fillRect(rect.x, rect.y, rect.width, rect.height);
    }
    else {

      JsonObject obj = new JsonObject();
      obj.addProperty("method", "fillPath");

      JsonArray segments = new JsonArray();

      for (PathIterator pi = s.getPathIterator(null); !pi.isDone(); pi.next()) {
        double[] coords = new double[6];
        int pathSegmentType = pi.currentSegment(coords);
        int windingRule = pi.getWindingRule();

        JsonArray points = new JsonArray();

        for (int i = 0; i < 6; i += 2) {
          JsonObject point = new JsonObject();
          point.addProperty("x", coords[i]);
          point.addProperty("y", coords[i + 1]);
          points.add(point);
        }

        JsonObject segment = new JsonObject();
        segment.add("points", points);
        segment.addProperty("type", pathSegmentType);
        segment.addProperty("rule", windingRule);
        segments.add(segment);
      }

      obj.add("path", segments);
      addCommand(obj);
      //JsonObject obj = new JsonObject();
      //obj.addProperty("method", "fill");
      //addCommand(obj);

    }
    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "fill");
    //addCommand(obj);

  }

  @Override
  public boolean hit(Rectangle rect, Shape s, boolean onStroke) {
    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "hit");
    //addCommand(obj);


    return false;
  }

  @Override
  public GraphicsConfiguration getDeviceConfiguration() {
    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "getDeviceConfiguration");
    //addCommand(obj);
    return _configuration;
  }

  private AlphaComposite lastAlphaComposite = null;

  @Override
  public void setComposite(Composite comp) {

    AlphaComposite ac = (comp instanceof AlphaComposite) ? (AlphaComposite)comp : null;
    if (ac != null) {

      if (lastAlphaComposite != null) {
        if (lastAlphaComposite.getAlpha() == ac.getAlpha() && lastAlphaComposite.getRule() == ac.getRule()) {
          return;
        }
      }

      JsonObject obj = new JsonObject();
      obj.addProperty("method", "setCompositeAlpha");
      obj.addProperty("alpha", ac.getAlpha());
      obj.addProperty("rule", ac.getRule());
      addCommand(obj);

      lastAlphaComposite = ac;

      return;
    }

    lastAlphaComposite = null;
    int i = 1;
    i++;
    // ToDo
  }

  private static JsonObject pointToJson(Point2D point) {
    JsonObject obj = new JsonObject();
    obj.addProperty("x", point.getX());
    obj.addProperty("y", point.getY());
    return obj;
  }

  private static JsonArray colorsToJsonArray(Color[] colors) {

    JsonArray array = new JsonArray();
    for (Color color : colors) {
      array.add(color.getRGB());
    }

    return array;
  }

  private static JsonArray floatsToJsonArray(float[] floats) {

    JsonArray array = new JsonArray();
    for (float flt : floats) {
      array.add(flt);
    }

    return array;
  }

  @Override
  public void setPaint(Paint paint) {
    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "setPaint");
    //addCommand(obj);

    org.apache.batik.ext.awt.LinearGradientPaint lgp =
      paint instanceof org.apache.batik.ext.awt.LinearGradientPaint ? (org.apache.batik.ext.awt.LinearGradientPaint)paint : null;
    if (lgp != null) {
      JsonObject obj = new JsonObject();
      obj.addProperty("method", "setPaintLinearGradient");

      obj.add("start", pointToJson(lgp.getStartPoint()));
      obj.add("end", pointToJson(lgp.getEndPoint()));

      obj.addProperty("transparency", lgp.getTransparency());
      obj.add("colors", colorsToJsonArray(lgp.getColors()));
      obj.add("fractions", floatsToJsonArray(lgp.getFractions()));

      obj.add("gradientTransform", transformToJson(lgp.getTransform()));

      addCommand(obj);
      return;
    }

    GradientPaint gp = paint instanceof GradientPaint ? (GradientPaint)paint : null;
    if (gp != null) {
      JsonObject obj = new JsonObject();
      obj.add("p1", pointToJson(gp.getPoint1()));
      obj.add("p2", pointToJson(gp.getPoint2()));
      obj.addProperty("color1", gp.getColor1().getRGB());
      obj.addProperty("color2", gp.getColor1().getRGB());
      return;
    }

    Color color = paint instanceof Color ? (Color)paint : null;
    if (color != null) {

      JsonObject obj = new JsonObject();
      obj.addProperty("method", "setPaintColor");
      obj.addProperty("color", color.getRGB());
      return;
    }
    else {
      int i = 1;
      i++;
    }

    // ToDo
  }

  @Override
  public void setStroke(Stroke s) {
    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "setStroke");
    //addCommand(obj);

    BasicStroke bs = s instanceof BasicStroke ? (BasicStroke)s : null;
    if (bs != null) {
      JsonObject obj = new JsonObject();
      obj.addProperty("method", "setBasicStroke");

      obj.addProperty("width", bs.getLineWidth());
      obj.addProperty("join", bs.getLineJoin());

      obj.addProperty("cap", bs.getEndCap());
      obj.addProperty("miterlimit", bs.getMiterLimit());
      obj.addProperty("dash_phase", bs.getDashPhase());

      if (bs.getDashArray() != null) {
        obj.add("dash", floatsToJsonArray(bs.getDashArray()));
      }

      addCommand(obj);
      return;
    }
    else {
      // ToDo
      int i = 1;
      i++;
    }
  }

  RenderingHints hints = makeHints(null);

  RenderingHints makeHints(Map var1) {
    RenderingHints var2 = new RenderingHints(var1);
    //var2.put(SunHints.KEY_RENDERING, SunHints.Value.get(0, this.renderHint));
    //var2.put(SunHints.KEY_ANTIALIASING, SunHints.Value.get(1, this.antialiasHint));
    //var2.put(SunHints.KEY_TEXT_ANTIALIASING, SunHints.Value.get(2, this.textAntialiasHint));
    //var2.put(SunHints.KEY_FRACTIONALMETRICS, SunHints.Value.get(3, this.fractionalMetricsHint));
    //var2.put(SunHints.KEY_TEXT_ANTIALIAS_LCD_CONTRAST, this.lcdTextContrast);
    //Object var3;
    //switch(this.interpolationHint) {
    //    case 0:
    //        var3 = SunHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
    //        break;
    //    case 1:
    //        var3 = SunHints.VALUE_INTERPOLATION_BILINEAR;
    //        break;
    //    case 2:
    //        var3 = SunHints.VALUE_INTERPOLATION_BICUBIC;
    //        break;
    //    default:
    //        var3 = null;
    //}
    //
    //if (var3 != null) {
    //    var2.put(SunHints.KEY_INTERPOLATION, var3);
    //}
    //
    //var2.put(SunHints.KEY_STROKE_CONTROL, SunHints.Value.get(8, this.strokeHint));
    return var2;
  }

  @Override
  public void setRenderingHint(Key hintKey, Object hintValue) {

    if(hintKey == null ||hintValue == null) {
      return;
    }

    JsonObject obj = new JsonObject();
    obj.addProperty("method", "setRenderingHint");
    addCommand(obj);

    hints.put(hintKey, hintValue);
  }

  @Override
  public Object getRenderingHint(Key hintKey) {
    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "getRenderingHint");
    //addCommand(obj);
    return hints.get(hintKey);
  }

  @Override
  public void setRenderingHints(Map<?, ?> hints1) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "setRenderingHints");
    addCommand(obj);

    hints.clear();
    hints.putAll(hints1);
  }

  @Override
  public void addRenderingHints(Map<?, ?> hints1) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "addRenderingHints");
    addCommand(obj);

    hints.putAll(hints1);
  }

  @Override
  public RenderingHints getRenderingHints() {
    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "getRenderingHints");
    //addCommand(obj);
    return hints;
  }

  @Override
  public void translate(int x, int y) {

    if (x == 0 && y == 0) {
      return;
    }

    getTransform().translate(x, y);

    JsonObject obj = new JsonObject();
    obj.addProperty("method", "translate");
    obj.addProperty("x", x);
    obj.addProperty("y", y);
    addCommand(obj);
  }

  @Override
  public void translate(double tx, double ty) {

    if (tx == 0 && ty == 0) {
      return;
    }

    getTransform().translate(tx, ty);

    JsonObject obj = new JsonObject();
    obj.addProperty("method", "translateA");
    obj.addProperty("tx", tx);
    obj.addProperty("ty", ty);
    addCommand(obj);
  }

  @Override
  public void rotate(double theta) {

    getTransform().rotate(theta);

    JsonObject obj = new JsonObject();
    obj.addProperty("method", "rotate");
    obj.addProperty("theta", theta);
    addCommand(obj);
  }

  @Override
  public void rotate(double theta, double x, double y) {

    getTransform().rotate(theta, x, y);

    JsonObject obj = new JsonObject();
    obj.addProperty("method", "rotateA");
    obj.addProperty("theta", theta);
    obj.addProperty("x", x);
    obj.addProperty("y", y);
    addCommand(obj);
  }

  @Override
  public void scale(double sx, double sy) {

    getTransform().scale(sx, sy);

    JsonObject obj = new JsonObject();
    obj.addProperty("method", "scale");
    obj.addProperty("sx", sx);
    obj.addProperty("sy", sy);
    addCommand(obj);
  }

  @Override
  public void shear(double shx, double shy) {

    getTransform().shear(shx, shy);

    JsonObject obj = new JsonObject();
    obj.addProperty("method", "shear");
    obj.addProperty("shx", shx);
    obj.addProperty("shy", shy);
    addCommand(obj);
  }

  //ToDo need same queue as we already did with clipping.

  private final ArrayList<AffineTransform> transform = new ArrayList<>();

  @Override
  public void transform(AffineTransform Tx) {

    if(Tx.isIdentity()) {
      return;
    }

    getTransform().concatenate(Tx);

    JsonObject obj = new JsonObject();
    //obj.addProperty("method", "transform");
    obj.addProperty("method", "setTransform");
    obj.add("tx", transformToJson(getTransform()));
    addCommand(obj);
  }

  @Override
  public void setTransform(AffineTransform Tx) {

    if(Tx == getTransform()) {
      return;
    }

    JsonObject obj = new JsonObject();
    obj.addProperty("method", "setTransform");
    obj.add("tx", transformToJson(Tx));

    addCommand(obj);

    getTransform().setTransform(Tx);
  }

  @Override
  public AffineTransform getTransform() {
    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "getTransform");
    //addCommand(obj);
    return transform.get(transform.size() - 1);
  }

  @Override
  public Paint getPaint() {
    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "getPaint");
    //addCommand(obj);
    return Color.black;
  }

  @Override
  public Composite getComposite() {
    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "getComposite");
    //addCommand(obj);
    return AlphaComposite.SrcOver;
  }

  private Color background = Color.white;

  @Override
  public void setBackground(Color color) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "setBackground");
    obj.addProperty("color", color.getRGB());
    addCommand(obj);

    background = color;
  }

  @Override
  public Color getBackground() {
    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "getBackground");
    //addCommand(obj);
    return background;
  }

  @Override
  public Stroke getStroke() {
    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "getStroke");
    //addCommand(obj);
    return new BasicStroke();
  }

  @Override
  public FontRenderContext getFontRenderContext() {
    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "getFontRenderContext");
    //addCommand(obj);

    return new FontRenderContext(new AffineTransform(), false, false);
  }


  private Color color = Color.black;

  @Override
  public Color getColor() {

    // Клиенту всё равно
    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "getColor");
    //addCommand(obj);

    return color;
  }

  @Override
  public void setColor(Color color) {
    if (color == null) {
      return;
    }
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "setColor");
    obj.addProperty("color", color.getRGB());
    addCommand(obj);

    this.color = color;
  }

  @Override
  public void setPaintMode() {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "setPaintMode");
    addCommand(obj);
  }

  @Override
  public void setXORMode(Color c1) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "setXORMode");
    obj.addProperty("color", c1.getRGB());
    addCommand(obj);
  }

  @Override
  public Font getFont() {
    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "getFont");
    //addCommand(obj);
    return font;
  }

  private Font font = new Font(Font.DIALOG, Font.PLAIN, 12);

  @Override
  public void setFont(Font font) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "setFont");
    if (font != null) {
      obj.addProperty("name", font.getName());
      obj.addProperty("size", font.getSize());
    }
    addCommand(obj);

    this.font = font;
  }

  @Override
  public FontMetrics getFontMetrics(Font f) {

    // Команда такая не нужна на клиенте
    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "getFontMetrics");
    //obj.addProperty("todo", true);
    //addCommand(obj);

    return new GFontMetrics(f);
  }

  private final ArrayList<Shape> clip = new ArrayList<>();

  @Override
  public Rectangle getClipBounds() {

    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "getClipBounds");
    //
    //addCommand(obj);
    return clip.get(clip.size() - 1) == null
           ? new Rectangle(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE)
           : clip.get(clip.size() - 1).getBounds();
  }

  private static void addRectToJson(JsonObject obj, int x, int y, int width, int height) {
    obj.addProperty("x", x);
    obj.addProperty("y", y);
    obj.addProperty("width", width);
    obj.addProperty("height", height);
  }

  @Override
  public void clipRect(int x, int y, int w, int h) {
    clip(new Rectangle(x, y, w, h));
  }

  @Override
  public void setClip(int x, int y, int w, int h) {
    setClip(new Rectangle(x, y, w, h));
  }

  //@Override
  //public void clipRect(int x, int y, int width, int height) {
  //    JsonObject obj = new JsonObject();
  //    obj.addProperty("method", "clipRect");
  //    addRectToJson(obj, x,y,width,height);
  //    addCommand(obj);
  //
  //    if(clip.get(clip.size()-1) == null) {
  //        clip.set(clip.size() - 1, new Rectangle(x, y, width, height));
  //    } else {
  //        clip.get(clip.size() - 1).intersects(new Rectangle(x, y, width, height));
  //    }
  //}
  //
  //@Override
  //public void setClip(int x, int y, int width, int height) {
  //    JsonObject obj = new JsonObject();
  //    obj.addProperty("method", "setClip");
  //    addRectToJson(obj, x,y,width,height);
  //    addCommand(obj);
  //
  //    this.clip.set(this.clip.size()-1,  new Rectangle(x,y,width,height));
  //}

  public Shape untransformShape(Shape s) throws NoninvertibleTransformException {
    if (s == null) {
      return null;
    }

    return transformShape(getTransform().createInverse(), s);
  }

  @Override
  public Shape getClip() {
    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "getClip");
    //obj.addProperty("todo", true);
    //addCommand(obj);

    Shape clip1 = clip.get(clip.size() - 1);

    if (clip1 == null) {
      return _configuration.getBounds();
    }

    try {
      return untransformShape(clip1);
    }
    catch (Exception e) {
      e.printStackTrace();
      return clip1;
    }
  }


  /*
   * Intersect two Shapes by the simplest method, attempting to produce
   * a simplified result.
   * The boolean arguments keep1 and keep2 specify whether or not
   * the first or second shapes can be modified during the operation
   * or whether that shape must be "kept" unmodified.
   */
  Shape intersectShapes(Shape s1, Shape s2, boolean keep1, boolean keep2) {
    if (s1 instanceof Rectangle && s2 instanceof Rectangle) {
      return ((Rectangle)s1).intersection((Rectangle)s2);
    }
    if (s1 instanceof Rectangle2D) {
      return intersectRectShape((Rectangle2D)s1, s2, keep1, keep2);
    }
    else if (s2 instanceof Rectangle2D) {
      return intersectRectShape((Rectangle2D)s2, s1, keep2, keep1);
    }
    return intersectByArea(s1, s2, keep1, keep2);
  }

  /*
   * Intersect a Rectangle with a Shape by the simplest method,
   * attempting to produce a simplified result.
   * The boolean arguments keep1 and keep2 specify whether or not
   * the first or second shapes can be modified during the operation
   * or whether that shape must be "kept" unmodified.
   */
  Shape intersectRectShape(Rectangle2D r, Shape s,
                           boolean keep1, boolean keep2) {
    if (s instanceof Rectangle2D) {
      Rectangle2D r2 = (Rectangle2D)s;
      Rectangle2D outrect;
      if (!keep1) {
        outrect = r;
      }
      else if (!keep2) {
        outrect = r2;
      }
      else {
        outrect = new Rectangle2D.Float();
      }
      double x1 = Math.max(r.getX(), r2.getX());
      double x2 = Math.min(r.getX() + r.getWidth(),
                           r2.getX() + r2.getWidth());
      double y1 = Math.max(r.getY(), r2.getY());
      double y2 = Math.min(r.getY() + r.getHeight(),
                           r2.getY() + r2.getHeight());

      if (((x2 - x1) < 0) || ((y2 - y1) < 0))
      // Width or height is negative. No intersection.
      {
        outrect.setFrameFromDiagonal(0, 0, 0, 0);
      }
      else {
        outrect.setFrameFromDiagonal(x1, y1, x2, y2);
      }
      return outrect;
    }
    if (r.contains(s.getBounds2D())) {
      if (keep2) {
        s = cloneShape(s);
      }
      return s;
    }
    return intersectByArea(r, s, keep1, keep2);
  }

  protected static Shape cloneShape(Shape s) {
    return new GeneralPath(s);
  }

  /*
   * Intersect two Shapes using the Area class.  Presumably other
   * attempts at simpler intersection methods proved fruitless.
   * The boolean arguments keep1 and keep2 specify whether or not
   * the first or second shapes can be modified during the operation
   * or whether that shape must be "kept" unmodified.
   * @see #intersectShapes
   * @see #intersectRectShape
   */
  Shape intersectByArea(Shape s1, Shape s2, boolean keep1, boolean keep2) {
    Area a1, a2;

    // First see if we can find an overwriteable source shape
    // to use as our destination area to avoid duplication.
    if (!keep1 && (s1 instanceof Area)) {
      a1 = (Area)s1;
    }
    else if (!keep2 && (s2 instanceof Area)) {
      a1 = (Area)s2;
      s2 = s1;
    }
    else {
      a1 = new Area(s1);
    }

    if (s2 instanceof Area) {
      a2 = (Area)s2;
    }
    else {
      a2 = new Area(s2);
    }

    a1.intersect(a2);
    if (a1.isRectangular()) {
      return a1.getBounds();
    }

    return a1;
  }

  private void setClipCommand(Shape s) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "setClip");
    addCommand(obj);

    Rectangle r = (s instanceof Rectangle) ? (Rectangle)s : null;
    if (r != null) {
      obj.addProperty("x", r.x);
      obj.addProperty("y", r.y);
      obj.addProperty("width", r.width);
      obj.addProperty("height", r.height);
      return;
    }

    Rectangle2D r2d = (s instanceof Rectangle2D) ? (Rectangle2D)s : null;
    if (r2d != null) {
      obj.addProperty("x", (int)r2d.getX());
      obj.addProperty("y", (int)r2d.getY());
      obj.addProperty("width", (int)r2d.getWidth());
      obj.addProperty("height", (int)r2d.getHeight());
      return;
    }

    Path2D p2d = (s instanceof Path2D) ? (Path2D)s : null;
    if (p2d != null) {
      Rectangle rect = p2d.getBounds();
      obj.addProperty("x", rect.x);
      obj.addProperty("y", rect.y);
      obj.addProperty("width", rect.width);
      obj.addProperty("height", rect.height);
      return;
    }

    int i = 1;
    i++;
  }


  protected static Shape transformShape(AffineTransform tx, Shape clip) {
    if (clip == null) {
      return null;
    }

    if (clip instanceof Rectangle2D) {
      Rectangle2D rect = (Rectangle2D)clip;
      double matrix[] = new double[4];
      matrix[0] = rect.getX();
      matrix[1] = rect.getY();
      matrix[2] = matrix[0] + rect.getWidth();
      matrix[3] = matrix[1] + rect.getHeight();

      tx.transform(matrix, 0, matrix, 0, 2);
      //fixRectangleOrientation(matrix, rect);
      return new Rectangle2D.Double(matrix[0], matrix[1],
                                    matrix[2] - matrix[0],
                                    matrix[3] - matrix[1]);
    }

    if (tx.isIdentity()) {
      return cloneShape(clip);
    }

    return tx.createTransformedShape(clip);
  }

  @Override
  public void clip(Shape s) {

    s = transformShape(getTransform(), s);

    Shape usrClip = clip.get(clip.size() - 1);

    if(s.getBounds().isEmpty()) {
      int i = 1;
      i++;
    }

    if (usrClip != null) {
      Shape s1 = intersectShapes(usrClip, s, true, true);
      if(s1.getBounds().isEmpty()) {
        int i = 1;
        i++;
      }
      s = s1;
    }

    this.clip.set(this.clip.size() - 1, s);

    setClipCommand(s);
  }

  @Override
  public void setClip(Shape s) {
    s = transformShape(getTransform(), s);
    this.clip.set(this.clip.size() - 1, s);

    setClipCommand(s);
  }

  @Override
  public void copyArea(int x, int y, int width, int height, int dx, int dy) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "copyArea");
    obj.addProperty("x", x);
    obj.addProperty("y", y);
    obj.addProperty("width", width);
    obj.addProperty("height", height);
    obj.addProperty("dx", dx);
    obj.addProperty("dy", dy);
    addCommand(obj);
  }

  @Override
  public void drawLine(int x1, int y1, int x2, int y2) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "drawLine");
    obj.addProperty("x1", x1);
    obj.addProperty("y1", y1);
    obj.addProperty("x2", x2);
    obj.addProperty("y2", y2);
    addCommand(obj);
  }

  @Override
  public void fillRect(int x, int y, int width, int height) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "fillRect");
    addRectToJson(obj, x, y, width, height);
    addCommand(obj);
  }

  @Override
  public void clearRect(int x, int y, int width, int height) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "clearRect");
    addRectToJson(obj, x, y, width, height);
    addCommand(obj);
  }

  @Override
  public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "drawRoundRect");
    addRectToJson(obj, x, y, width, height);
    obj.addProperty("arcWidth", arcWidth);
    obj.addProperty("arcHeight", arcHeight);
    addCommand(obj);
  }

  @Override
  public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "fillRoundRect");
    addRectToJson(obj, x, y, width, height);
    obj.addProperty("arcWidth", arcWidth);
    obj.addProperty("arcHeight", arcHeight);
    addCommand(obj);
  }

  @Override
  public void drawOval(int x, int y, int width, int height) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "drawOval");
    addRectToJson(obj, x, y, width, height);
    addCommand(obj);
  }

  @Override
  public void fillOval(int x, int y, int width, int height) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "fillOval");
    obj.addProperty("x", x);
    obj.addProperty("y", y);
    obj.addProperty("width", width);
    obj.addProperty("height", height);
    addCommand(obj);
  }

  @Override
  public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "drawArc");
    addRectToJson(obj, x, y, width, height);
    obj.addProperty("startAngle", startAngle);
    obj.addProperty("arcAngle", arcAngle);
    addCommand(obj);
  }

  @Override
  public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "fillArc");
    addRectToJson(obj, x, y, width, height);
    obj.addProperty("startAngle", startAngle);
    obj.addProperty("arcAngle", arcAngle);

    addCommand(obj);
  }

  private void addPoints(JsonObject obj, int[] xPoints, int[] yPoints, int nPoints) {
    JsonArray xArray = new JsonArray(xPoints.length);
    for (int pointX : xPoints) {
      xArray.add(pointX);
    }

    JsonArray yArray = new JsonArray(xPoints.length);
    for (int pointY : yPoints) {
      yArray.add(pointY);
    }

    obj.add("xPoints", xArray);
    obj.add("yPoints", yArray);
    obj.addProperty("nPoints", nPoints);
  }

  @Override
  public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "drawPolyline");
    addPoints(obj, xPoints, yPoints, nPoints);
    addCommand(obj);
  }

  @Override
  public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "drawPolygon");
    addPoints(obj, xPoints, yPoints, nPoints);
    addCommand(obj);
  }

  @Override
  public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "fillPolygon");
    addPoints(obj, xPoints, yPoints, nPoints);
    addCommand(obj);
  }


  private static String imageToString(Image img) {

    if (!(img instanceof BufferedImage)) {
      return "";
    }

    byte[] imageInByte = null;
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ImageIO.write((BufferedImage)img, "png", baos);
      baos.flush();
      imageInByte = baos.toByteArray();
      baos.close();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    byte[] encoded = Base64.getEncoder().encode(imageInByte);

    return new String(encoded);
  }

  @Override
  public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs) {
    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "drawImage");
    //obj.addProperty("img", imageToString(img));
    //obj.add("xform", transformToJson(xform));
    //
    //addCommand(obj);

    return false;
  }

  @Override
  public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) {
    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "drawImageA");
    //obj.addProperty("x",x);
    //obj.addProperty("y",y);
    //obj.addProperty("img", imageToString(img));
    //addCommand(obj);
  }

  @Override
  public boolean drawImage(Image img, int x, int y, ImageObserver observer) {

    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "drawImageB");
    //obj.addProperty("x",x);
    //obj.addProperty("y",y);
    //obj.addProperty("width",img.getWidth(observer));
    //obj.addProperty("height",img.getHeight(observer));
    //obj.addProperty("img",imageToString(img));
    //addCommand(obj);

    return false;
  }

  @Override
  public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer) {

    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "drawImageC");
    //obj.addProperty("x",x);
    //obj.addProperty("y",y);
    //obj.addProperty("width",width);
    //obj.addProperty("height",height);
    //
    //obj.addProperty("img",imageToString(img));
    //
    //addCommand(obj);
    return true;
  }

  @Override
  public boolean drawImage(Image img, int x, int y, Color bgcolor, ImageObserver observer) {
    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "drawImageD");
    //obj.addProperty("todo", true);
    //addCommand(obj);
    return true;
  }

  @Override
  public boolean drawImage(Image img, int x, int y, int width, int height, Color bgcolor, ImageObserver observer) {
    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "drawImageE");
    //obj.addProperty("todo", true);
    //addCommand(obj);

    return true;
  }

  @Override
  public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, ImageObserver observer) {
    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "drawImageF");
    //obj.addProperty("todo", true);
    //addCommand(obj);

    return true;
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
    //JsonObject obj = new JsonObject();
    //obj.addProperty("method", "drawImageG");
    //obj.addProperty("todo", true);
    //addCommand(obj);

    return true;
  }

  @Override
  public void dispose() {
    JsonObject obj = new JsonObject();
    obj.addProperty("method", "dispose");
    addCommand(obj);

    if (!clip.isEmpty()) {
      clip.remove(clip.size() - 1);
   }

    if (!transform.isEmpty()) {
      transform.remove(transform.size() - 1);
    }
  }
}
