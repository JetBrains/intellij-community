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
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
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

public class GGraphics2D extends Graphics2D {
    private GraphicsConfiguration _configuration;

//    private BufferedImage buffer;


    public final static ArrayList<JsonElement> commands = new ArrayList<>();


    public GGraphics2D(GraphicsConfiguration configuration) {
        _configuration = configuration;

        //     buffer = ((GGraphicsConfiguration)configuration).buffer;
        clip.add(_configuration.getBounds());
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


        clip.add(null);

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
        for(int i=0;i<flatmatrix.length;i++) {
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

        if(s instanceof Rectangle) {
            Rectangle rect = (Rectangle) s;
            fillRect(rect.x, rect.y, rect.width, rect.height);
        } else {

            JsonObject obj = new JsonObject();
            obj.addProperty("method", "fillPath");

            JsonArray segments = new JsonArray();

            for ( PathIterator pi = s.getPathIterator(null); !pi.isDone(); pi.next()) {
                double[] coords = new double[6];
                int pathSegmentType = pi.currentSegment(coords);
                int windingRule = pi.getWindingRule();

                JsonArray points = new JsonArray();

                for(int i=0;i<6;i+=2)
                {
                    JsonObject point = new JsonObject();
                    point.addProperty("x", coords[i]);
                    point.addProperty("y", coords[i+1]);
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
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "getDeviceConfiguration");
        addCommand(obj);
        return _configuration;
    }

    @Override
    public void setComposite(Composite comp) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "setComposite");
        addCommand(obj);
    }

    @Override
    public void setPaint(Paint paint) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "setPaint");
        addCommand(obj);
    }

    @Override
    public void setStroke(Stroke s) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "setStroke");
        addCommand(obj);
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
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "translate");
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        addCommand(obj);
    }

    @Override
    public void translate(double tx, double ty) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "translateA");
        obj.addProperty("tx", tx);
        obj.addProperty("ty", ty);
        addCommand(obj);
    }

    @Override
    public void rotate(double theta) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "rotate");
        obj.addProperty("theta", theta);
        addCommand(obj);
    }

    @Override
    public void rotate(double theta, double x, double y) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "rotateA");
        obj.addProperty("theta", theta);
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        addCommand(obj);
    }

    @Override
    public void scale(double sx, double sy) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "scale");
        obj.addProperty("sx", sx);
        obj.addProperty("sy", sy);
        addCommand(obj);
    }

    @Override
    public void shear(double shx, double shy) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "shear");
        obj.addProperty("shx", shx);
        obj.addProperty("shy", shy);
        addCommand(obj);
    }

    private AffineTransform transform = new AffineTransform();

    @Override
    public void transform(AffineTransform Tx) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "transform");
        addCommand(obj);
    }

    @Override
    public void setTransform(AffineTransform Tx) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "setTransform");
        addCommand(obj);

        transform = Tx;
    }

    @Override
    public AffineTransform getTransform() {
        //JsonObject obj = new JsonObject();
        //obj.addProperty("method", "getTransform");
        //addCommand(obj);
        return transform;
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

    private Color background =  Color.white;

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
    public void clip(Shape s) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "clip");
        addCommand(obj);
    }

    @Override
    public FontRenderContext getFontRenderContext() {
        //JsonObject obj = new JsonObject();
        //obj.addProperty("method", "getFontRenderContext");
        //addCommand(obj);

        return new FontRenderContext(new AffineTransform(), false, false);
    }


    private Color color =  Color.black;

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
        obj.addProperty("color",  c1.getRGB());
        addCommand(obj);
    }

    @Override
    public Font getFont() {
        //JsonObject obj = new JsonObject();
        //obj.addProperty("method", "getFont");
        //addCommand(obj);
        return font;
    }

    private Font font =  new Font(Font.DIALOG, Font.PLAIN, 12);

    @Override
    public void setFont(Font font) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "setFont");
        if(font != null) {
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

    private ArrayList<Shape> clip = new ArrayList<>();

    @Override
    public Rectangle getClipBounds() {

        //JsonObject obj = new JsonObject();
        //obj.addProperty("method", "getClipBounds");
        //
        //addCommand(obj);
        return clip.get(clip.size()-1) == null ? new Rectangle(0,0, Integer.MAX_VALUE,Integer.MAX_VALUE) : clip.get(clip.size()-1).getBounds();
    }

    @Override
    public void clipRect(int x, int y, int width, int height) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "clipRect");
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        obj.addProperty("width", width);
        obj.addProperty("height", height);
        addCommand(obj);

        if(clip.get(clip.size()-1) == null) {
            clip.set(clip.size() - 1, new Rectangle(x, y, width, height));
        } else {
            clip.get(clip.size() - 1).intersects(new Rectangle(x, y, width, height));
        }
    }

    @Override
    public void setClip(int x, int y, int width, int height) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "setClip");
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        obj.addProperty("width", width);
        obj.addProperty("height", height);
        addCommand(obj);

        this.clip.set(this.clip.size()-1,  new Rectangle(x,y,width,height));
    }

    @Override
    public Shape getClip() {
        //JsonObject obj = new JsonObject();
        //obj.addProperty("method", "getClip");
        //obj.addProperty("todo", true);
        //addCommand(obj);

        Shape clip1 = clip.get(clip.size()-1);

        if(clip1 == null) {
            clip1 = _configuration.getBounds();
        }

        return clip1;
    }


    @Override
    public void setClip(Shape clip) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "setClipA");

        // In our cases clip - always null and this method is actually called to reset clipping state.

        addCommand(obj);

        this.clip.set(this.clip.size()-1,  clip);
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
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        obj.addProperty("width", width);
        obj.addProperty("height", height);
        addCommand(obj);
    }

    @Override
    public void clearRect(int x, int y, int width, int height) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "clearRect");
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        obj.addProperty("width", width);
        obj.addProperty("height", height);
        addCommand(obj);
    }

    @Override
    public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "drawRoundRect");
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        obj.addProperty("width", width);
        obj.addProperty("height", height);
        obj.addProperty("arcWidth", arcWidth);
        obj.addProperty("arcHeight", arcHeight);
        addCommand(obj);
    }

    @Override
    public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "fillRoundRect");
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        obj.addProperty("width", width);
        obj.addProperty("height", height);
        obj.addProperty("arcWidth", arcWidth);
        obj.addProperty("arcHeight", arcHeight);
        addCommand(obj);
    }

    @Override
    public void drawOval(int x, int y, int width, int height) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "drawOval");
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        obj.addProperty("width", width);
        obj.addProperty("height", height);
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
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        obj.addProperty("width", width);
        obj.addProperty("height", height);
        obj.addProperty("startAngle", startAngle);
        obj.addProperty("arcAngle", arcAngle);
        addCommand(obj);
    }

    @Override
    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "fillArc");
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        obj.addProperty("width", width);
        obj.addProperty("height", height);
        obj.addProperty("startAngle", startAngle);
        obj.addProperty("arcAngle", arcAngle);

        addCommand(obj);
    }

    @Override
    public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "drawPolyline");

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

        addCommand(obj);
    }

    @Override
    public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "drawPolygon");

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

        addCommand(obj);
    }

    @Override
    public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "fillPolygon");

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

        addCommand(obj);
    }

    @Override
    public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "drawImage");
        obj.addProperty("img", imageToString(img));
        obj.add("xform", transformToJson(xform));

        addCommand(obj);

        return false;
    }

    @Override
    public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "drawImageA");
        obj.addProperty("x",x);
        obj.addProperty("y",y);
        obj.addProperty("img", imageToString(img));
        addCommand(obj);
    }

    @Override
    public boolean drawImage(Image img, int x, int y, ImageObserver observer) {

        JsonObject obj = new JsonObject();
        obj.addProperty("method", "drawImageB");
        obj.addProperty("x",x);
        obj.addProperty("y",y);
        obj.addProperty("width",img.getWidth(observer));
        obj.addProperty("height",img.getHeight(observer));
        obj.addProperty("img",imageToString(img));
        addCommand(obj);

        return false;
    }

    private static String imageToString(Image img) {

        if(! (img instanceof BufferedImage)) {
            return "";
        }

        byte[] imageInByte = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write((BufferedImage)img, "png", baos);
            baos.flush();
            imageInByte = baos.toByteArray();
            baos.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
        byte[] encoded = Base64.getEncoder().encode(imageInByte);

        return  new String(encoded);
    }

    @Override
    public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer) {

        JsonObject obj = new JsonObject();
        obj.addProperty("method", "drawImageC");
        obj.addProperty("x",x);
        obj.addProperty("y",y);
        obj.addProperty("width",width);
        obj.addProperty("height",height);

        obj.addProperty("img",imageToString(img));

        addCommand(obj);
        return true;
    }

    @Override
    public boolean drawImage(Image img, int x, int y, Color bgcolor, ImageObserver observer) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "drawImageD");
        obj.addProperty("todo", true);
        addCommand(obj);
        return true;
    }

    @Override
    public boolean drawImage(Image img, int x, int y, int width, int height, Color bgcolor, ImageObserver observer) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "drawImageE");
        obj.addProperty("todo", true);
        addCommand(obj);

        return true;
    }

    @Override
    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, ImageObserver observer) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "drawImageF");
        obj.addProperty("todo", true);
        addCommand(obj);

        return true;
    }

    @Override
    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, Color bgcolor, ImageObserver observer) {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "drawImageG");
        obj.addProperty("todo", true);
        addCommand(obj);

        return true;
    }

    @Override
    public void dispose() {
        JsonObject obj = new JsonObject();
        obj.addProperty("method", "dispose");
        addCommand(obj);

        if(clip.size() > 1)
            clip.remove(clip.size()-1);
    }
}
