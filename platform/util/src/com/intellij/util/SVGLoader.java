// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.LazyInitializer.NotNullValue;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBUI.ScaleContext;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.anim.dom.SVGDOMImplementation;
import org.apache.batik.anim.dom.SVGOMAnimatedLength;
import org.apache.batik.anim.dom.SVGOMRectElement;
import org.apache.batik.bridge.UserAgent;
import org.apache.batik.dom.AbstractDocument;
import org.apache.batik.transcoder.SVGAbstractTranscoder;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.apache.batik.util.XMLResourceDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.svg.SVGDocument;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.util.ui.JBUI.ScaleType.PIX_SCALE;

/**
 * @author tav
 */
public class SVGLoader {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.SVGLoader");
  private static SvgColorPatcher ourColorPatcher = null;

  public static final NotNullValue<Double> MAX_SIZE = new NotNullValue<Double>() {
    @NotNull
    @Override
    public Double initialize() {
      double maxSize = Integer.MAX_VALUE;
      if (!GraphicsEnvironment.isHeadless()) {
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        Rectangle bounds = device.getDefaultConfiguration().getBounds();
        AffineTransform tx = device.getDefaultConfiguration().getDefaultTransform();
        maxSize = (int)Math.max(bounds.width * tx.getScaleX(), bounds.height * tx.getScaleY());
      }
      return maxSize;
    }
  };

  private final TranscoderInput myInput;
  private final Size mySize;
  private BufferedImage myImage;

  private static class Size {
    final double width;
    final double height;

    static final int FALLBACK_SIZE = 16;

    Size(double width, double height) {
      this.width = width;
      this.height = height;
    }

    Size scale(double scale) {
      return new Size(width * scale, height * scale);
    }

    Size scaleToMaxSize() {
      double w = width;
      double h = height;
      if (w > MAX_SIZE.get() || h > MAX_SIZE.get()) {
        double k = w >= h ? w / MAX_SIZE.get() : h / MAX_SIZE.get();
        w /= k;
        h /= k;
      }
      return new Size(w, h);
    }

    @NotNull
    public static Size parse(@NotNull Document document) {
      Float width = parseSize(document, "width");
      Float height = parseSize(document, "height");
      if (width != null && height != null) {
        return new Size(width, height);
      }
      Size viewBox = parseViewBox(document);
      if (viewBox != null) {
        return viewBox;
      }
      return new Size(FALLBACK_SIZE, FALLBACK_SIZE);
    }

    @Nullable
    private static Float parseSize(@NotNull Document document, @NotNull String sizeName) {
      String value = document.getDocumentElement().getAttribute(sizeName);
      if (value.endsWith("px")) {
        try {
          return Float.parseFloat(value.substring(0, value.length() - 2));
        }
        catch (NumberFormatException ignored) {
        }
      }
      return null;
    }

    @Nullable
    private static Size parseViewBox(@NotNull Document document) {
      String value = document.getDocumentElement().getAttribute("viewBox");
      if (value == null || value.isEmpty()) {
        return null;
      }
      List<String> values = new ArrayList<String>(4);
      for (String token : StringUtil.tokenize(value, ", ")) {
        values.add(token);
      }

      if (values.size() == 4) {
        try {
          return new Size(Float.parseFloat(values.get(2)),
                          Float.parseFloat(values.get(3)));
        }
        catch (NumberFormatException ignored) {
        }
      }
      LOG.warn("SVG file " + ObjectUtils.notNull(document.getBaseURI(), "") +
               " 'viewBox' expected in format: 'x y width height' or 'x, y, width, height'");
      return null;
    }
  }

  private class MyTranscoder extends ImageTranscoder {
    @Override
    public BufferedImage createImage(int w, int h) {
      //noinspection UndesirableClassUsage
      return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    @Override
    public void writeImage(BufferedImage img, TranscoderOutput output) {
      SVGLoader.this.myImage = img;
    }

    @Override
    protected UserAgent createUserAgent() {
      return new SVGAbstractTranscoderUserAgent() {
        @Override
        public SVGDocument getBrokenLinkDocument(Element e, String url, String message) {
          LOG.warn(url + " " + message);
          return createFallbackPlaceholder();
        }
      };
    }
  }

  public static Image load(@NotNull URL url, float scale) throws IOException {
    return load(url, url.openStream(), scale);
  }

  public static Image load(@NotNull InputStream stream , float scale) throws IOException {
    return load(null, stream, scale);
  }

  public static Image load(@Nullable URL url, @NotNull InputStream stream , double scale) throws IOException {
    try {
      return new SVGLoader(url, stream, scale).createImage();
    }
    catch (TranscoderException ex) {
      throw new IOException(ex);
    }
  }

  /**
   * Loads an image with the specified {@code width} and {@code height}. Size specified in svg file is ignored.
   */
  public static Image load(@Nullable URL url, @NotNull InputStream stream, double width, double height) throws IOException {
    try {
      return new SVGLoader(url, stream, width, height, 1).createImage();
    }
    catch (TranscoderException ex) {
      throw new IOException(ex);
    }
  }

  public static <T extends BufferedImage> T loadHiDPI(@Nullable URL url, @NotNull InputStream stream , ScaleContext ctx) throws IOException {
    BufferedImage image = (BufferedImage)load(url, stream, ctx.getScale(PIX_SCALE));
    //noinspection unchecked
    return (T)ImageUtil.ensureHiDPI(image, ctx);
  }

  public static Couple<Integer> loadInfo(@Nullable URL url, @NotNull InputStream stream , double scale) throws IOException {
    SVGLoader loader = new SVGLoader(url, stream, scale);
    return Couple.of((int)loader.mySize.width, (int)loader.mySize.height);
  }

  public static double getMaxZoomFactor(@Nullable URL url, @NotNull InputStream stream, @NotNull ScaleContext ctx) throws IOException {
    SVGLoader loader = new SVGLoader(url, stream, ctx.getScale(PIX_SCALE));
    return Math.min(MAX_SIZE.get() / loader.mySize.width, MAX_SIZE.get() / loader.mySize.height);
  }

  private SVGLoader(@Nullable URL url, InputStream stream, double scale) throws IOException {
    this(url, stream, -1, -1, scale);
  }

  private SVGLoader(@Nullable URL url, InputStream stream, double width, double height, double scale) throws IOException {
    Document document;
    String uri = null;
    try {
      if (url != null && "jar".equals(url.getProtocol()) && stream != null) {
        // workaround for BATIK-1217
        url = new URL(url.getPath());
      }
      uri = url != null ? url.toURI().toString() : null;
    }
    catch (URISyntaxException ignore) {
    }
    document = new MySAXSVGDocumentFactory(XMLResourceDescriptor.getXMLParserClassName()).
      createDocument(uri, stream);
    if (document == null) {
      throw new IOException("document not created");
    }
    patchColors(document);
    myInput = new TranscoderInput(document);
    mySize = (width < 0 || height < 0 ? Size.parse(document) : new Size(width, height)).scale(scale);
  }

  private static void patchColors(Document document) {
    if (ourColorPatcher != null) {
      ourColorPatcher.patchColors(document.getDocumentElement());
    }
  }

  public static void setColorPatcher(@Nullable SvgColorPatcher colorPatcher) {
    ourColorPatcher = colorPatcher;
    IconLoader.clearCache();
  }

  private BufferedImage createImage() throws TranscoderException {
    Size size = mySize.scaleToMaxSize();
    MyTranscoder r = new MyTranscoder();
    r.addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH, new Float(size.width));
    r.addTranscodingHint(SVGAbstractTranscoder.KEY_HEIGHT, new Float(size.height));
    r.transcode(myInput, null);
    return myImage;
  }

  @NotNull
  private static SVGDocument createFallbackPlaceholder() {
    try {
      String fallbackIcon = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" viewBox=\"0 0 16 16\">\n" +
                            "  <rect x=\"1\" y=\"1\" width=\"14\" height=\"14\" fill=\"none\" stroke=\"red\" stroke-width=\"2\"/>\n" +
                            "  <line x1=\"1\" y1=\"1\" x2=\"15\" y2=\"15\" stroke=\"red\" stroke-width=\"2\"/>\n" +
                            "  <line x1=\"1\" y1=\"15\" x2=\"15\" y2=\"1\" stroke=\"red\" stroke-width=\"2\"/>\n" +
                            "</svg>\n";

      SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(XMLResourceDescriptor.getXMLParserClassName());
      return (SVGDocument)factory.createDocument(null, new StringReader(fallbackIcon));
    }
    catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public interface SvgColorPatcher {
    void patchColors(Element svg);
  }

  /**
   * A workaround for https://issues.apache.org/jira/browse/BATIK-1220
   */
  private static class MySAXSVGDocumentFactory extends SAXSVGDocumentFactory {
    MySAXSVGDocumentFactory(String parser) {
      super(parser);
      implementation = new MySVGDOMImplementation();
    }
  }

  private static class MySVGDOMImplementation extends SVGDOMImplementation {
    static {
      svg11Factories.put("rect", new SVGDOMImplementation.RectElementFactory() {
        @Override
        public Element create(String prefix, Document doc) {
          return new SVGOMRectElement(prefix, (AbstractDocument)doc) {
            @Override
            protected SVGOMAnimatedLength createLiveAnimatedLength(String ns, String ln, String def, short dir, boolean nonneg) {
              if (def == null && ("width".equals(ln) || "height".equals(ln))) {
                def = "0"; // used in case of missing width/height attr to avoid org.apache.batik.bridge.BridgeException
              }
              return super.createLiveAnimatedLength(ns, ln, def, dir, nonneg);
            }
          };
        }
      });
    }
  }
}
