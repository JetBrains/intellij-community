// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.LazyInitializer.NotNullValue;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBUIScale;
import com.intellij.util.ui.JBUIScale.ScaleContext;
import org.apache.batik.anim.dom.*;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.CursorManager;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.UserAgent;
import org.apache.batik.dom.AbstractDocument;
import org.apache.batik.transcoder.SVGAbstractTranscoder;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.apache.batik.util.XMLResourceDescriptor;
import org.apache.xmlgraphics.java2d.Dimension2DDouble;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.svg.SVGDocument;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Dimension2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * @author tav
 */
public class SVGLoader {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.SVGLoader");
  private static SvgColorPatcher ourColorPatcher = null;

  public static final int ICON_DEFAULT_SIZE = 16;

  public static final NotNullValue<Double> ICON_MAX_SIZE = new NotNullValue<Double>() {
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

  private final TranscoderInput myTranscoderInput;
  private final double myScale;
  private final double myOverriddenWidth;
  private final double myOverriddenHeight;
  private BufferedImage myImage;
  private MyTranscoder myTranscoder;

  private class MyTranscoder extends ImageTranscoder {
    float myOrigDocWidth;
    float myOrigDocHeight;

    protected MyTranscoder() {
      width = ICON_DEFAULT_SIZE;
      height = ICON_DEFAULT_SIZE;
    }

    @Override
    protected void setImageSize(float docWidth, float docHeight) {
      myOrigDocWidth = docWidth;
      myOrigDocHeight = docHeight;
      super.setImageSize((float)(docWidth * myScale), (float)(docHeight * myScale));
    }

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

    @Override
    public BridgeContext createBridgeContext(SVGOMDocument doc) {
      return super.createBridgeContext(doc);
    }
  }

  public static Image load(@NotNull URL url, float scale) throws IOException {
    return load(url, url.openStream(), scale);
  }

  public static Image load(@NotNull InputStream stream, float scale) throws IOException {
    return load(null, stream, scale);
  }

  public static Image load(@Nullable URL url, @NotNull InputStream stream, double scale) throws IOException {
    return load(url, stream, scale, null);
  }

  static Image load(@Nullable URL url, @NotNull InputStream stream, double scale, @Nullable Dimension2D docSize /*OUT*/) throws IOException {
    try {
      SVGLoader loader = new SVGLoader(url, stream, scale);
      Image img = loader.createImage();
      if (docSize != null) {
        docSize.setSize(loader.myTranscoder.myOrigDocWidth, loader.myTranscoder.myOrigDocHeight);
      }
      return img;
    }
    catch (TranscoderException ex) {
      if (docSize != null) docSize.setSize(0, 0);
      throw new IOException(ex);
    }
  }

  /**
   * Loads an image with the specified {@code width} and {@code height} (in user space). Size specified in svg file is ignored.
   */
  public static Image load(@Nullable URL url, @NotNull InputStream stream, @NotNull ScaleContext ctx, double width, double height) throws IOException {
    try {
      double s = ctx.getScale(JBUIScale.DerivedScaleType.PIX_SCALE);
      return new SVGLoader(url, stream, width * s, height * s, 1).createImage();
    }
    catch (TranscoderException ex) {
      throw new IOException(ex);
    }
  }

  /**
   * Loads a HiDPI-aware image with the specified {@code width} and {@code height} (in user space). Size specified in svg file is ignored.
   */
  public static <T extends BufferedImage> T loadHiDPI(@Nullable URL url,
                                                      @NotNull InputStream stream,
                                                      ScaleContext ctx,
                                                      double width,
                                                      double height) throws IOException {
    BufferedImage image = (BufferedImage)load(url, stream, ctx, width, height);
    @SuppressWarnings("unchecked") T t = (T)ImageUtil.ensureHiDPI(image, ctx);
    return t;
  }

  /**
   * Loads a HiDPI-aware image of the size specified in the svg file.
   */
  public static <T extends BufferedImage> T loadHiDPI(@Nullable URL url, @NotNull InputStream stream, ScaleContext ctx) throws IOException {
    BufferedImage image = (BufferedImage)load(url, stream, ctx.getScale(JBUIScale.DerivedScaleType.PIX_SCALE));
    @SuppressWarnings("unchecked") T t = (T)ImageUtil.ensureHiDPI(image, ctx);
    return t;
  }

  /** @deprecated Use {@link #loadHiDPI(URL, InputStream, ScaleContext)} */
  @Deprecated
  public static <T extends BufferedImage> T loadHiDPI(@Nullable URL url, @NotNull InputStream stream, JBUI.ScaleContext ctx) throws IOException {
    return loadHiDPI(url, stream, (ScaleContext)ctx);
  }

  public static Dimension2D getDocumentSize(@Nullable URL url, @NotNull InputStream stream, double scale) throws IOException {
    // In order to get the size we parse the whole document and build a tree ("GVT"), what might be too expensive.
    // So, to optimize we extract the svg header (possibly prepended with <?xml> header) and parse only it.
    // Assumes 8-bit encoding of the input stream (no one in theirs right mind would use wide characters for SVG anyway).
    BufferExposingByteArrayOutputStream buffer = new BufferExposingByteArrayOutputStream(100);
    byte[] bytes = new byte[3];
    boolean checkClosingBracket = false;
    int ch;
    while ((ch = stream.read()) != -1) {
      buffer.write(ch);
      if (ch == '<') {
        int n = stream.read(bytes, 0, 3);
        if (n == -1) break;
        buffer.write(bytes, 0, n);
        checkClosingBracket = n == 3 && bytes[0] == 's' && bytes[1] == 'v' && bytes[2] == 'g';
      }
      else if (checkClosingBracket && ch == '>') {
        buffer.write(new byte[]{'<', '/', 's', 'v', 'g', '>'});
        return new SVGLoader(url, new ByteArrayInputStream(buffer.getInternalBuffer(), 0, buffer.size()), scale).getDocumentSize();
      }
    }
    return new Dimension2DDouble(ICON_DEFAULT_SIZE * scale, ICON_DEFAULT_SIZE * scale);
  }

  public static double getMaxZoomFactor(@Nullable URL url, @NotNull InputStream stream, @NotNull ScaleContext ctx) throws IOException {
    SVGLoader loader = new SVGLoader(url, stream, ctx.getScale(JBUIScale.DerivedScaleType.PIX_SCALE));
    Dimension2D size = loader.getDocumentSize();
    return Math.min(ICON_MAX_SIZE.get() / size.getWidth(), ICON_MAX_SIZE.get() / size.getHeight());
  }

  private SVGLoader(@Nullable URL url, InputStream stream, double scale) throws IOException {
    this(url, stream, -1, -1, scale);
  }

  private SVGLoader(@Nullable URL url, InputStream stream, double width, double height, double scale) throws IOException {
    String uri = null;
    try {
      if (url != null && "jar".equals(url.getProtocol()) && stream != null) {
        // workaround for BATIK-1217
        url = new URL(url.getPath());
      }
      uri = url != null ? url.toURI().toString() : null;
    }
    catch (URISyntaxException ignore) { }

    Document document = new MySAXSVGDocumentFactory(XMLResourceDescriptor.getXMLParserClassName()).createDocument(uri, stream);
    if (document == null) {
      throw new IOException("document not created");
    }
    patchColors(url, document);
    myTranscoderInput = new TranscoderInput(document);
    myOverriddenWidth = width;
    myOverriddenHeight = height;
    myScale = scale;
  }

  private static void patchColors(URL url, Document document) {
    if (ourColorPatcher != null) {
      ourColorPatcher.patchColors(url, document.getDocumentElement());
    }
  }

  public static void setColorPatcher(@Nullable SvgColorPatcher colorPatcher) {
    ourColorPatcher = colorPatcher;
    IconLoader.clearCache();
  }

  // ideally Apache Batik should be fixed, because we don't use cursors at all
  public static void prepareBatikInAwt() {
    // force initialization to call WToolkit.createCustomCursor in EDT thread,
    // otherwise when our SVG loading is performed in a pooled thread, it can lead to deadlock
    // https://youtrack.jetbrains.com/issue/IDEA-209987
    CursorManager.DEFAULT_CURSOR.getType();
  }

  private BufferedImage createImage() throws TranscoderException {
    myTranscoder = new MyTranscoder();
    if (myOverriddenWidth != -1) {
      myTranscoder.addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH, new Float(myOverriddenWidth));
    }
    if (myOverriddenHeight != -1) {
      myTranscoder.addTranscodingHint(SVGAbstractTranscoder.KEY_HEIGHT, new Float(myOverriddenHeight));
    }
    myTranscoder.addTranscodingHint(SVGAbstractTranscoder.KEY_MAX_WIDTH, new Float(ICON_MAX_SIZE.get()));
    myTranscoder.addTranscodingHint(SVGAbstractTranscoder.KEY_MAX_HEIGHT, new Float(ICON_MAX_SIZE.get()));
    myTranscoder.transcode(myTranscoderInput, null);
    return myImage;
  }

  private Dimension2D getDocumentSize() {
    SVGOMDocument document = (SVGOMDocument)myTranscoderInput.getDocument();
    BridgeContext ctx = new MyTranscoder().createBridgeContext(document);
    new GVTBuilder().build(ctx, document);
    Dimension2D size = ctx.getDocumentSize();
    size.setSize(size.getWidth() * myScale, size.getHeight() * myScale);
    return size;
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
    @Deprecated
    default void patchColors(Element svg) {}

    default void patchColors(URL url, Element svg) {
      patchColors(svg);
    }
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
      SVGDOMImplementation.svg11Factories.put("rect", new SVGDOMImplementation.RectElementFactory() {
        @Override
        public Element create(String prefix, Document doc) {
          return new SVGOMRectElement(prefix, (AbstractDocument)doc) {
            @Override
            protected SVGOMAnimatedLength createLiveAnimatedLength(String ns, String ln, String def, short dir, boolean nonNeg) {
              if (def == null && ("width".equals(ln) || "height".equals(ln))) {
                def = "0"; // used in case of missing width/height attr to avoid org.apache.batik.bridge.BridgeException
              }
              return super.createLiveAnimatedLength(ns, ln, def, dir, nonNeg);
            }
          };
        }
      });
    }
  }
}