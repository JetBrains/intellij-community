// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.scale.DerivedScaleType;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.ui.svg.MyTranscoder;
import com.intellij.ui.svg.SaxSvgDocumentFactory;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBUI;
import org.apache.batik.anim.dom.SVGOMDocument;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.awt.*;
import java.awt.geom.Dimension2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author tav
 */
public final class SVGLoader {
  private static final byte[] DEFAULT_THEME = new byte[0];

  private static SvgElementColorPatcherProvider ourColorPatcher = null;

  private static final SVGLoaderCache ourCache = new SVGLoaderCache() {
    @NotNull
    @Override
    protected Path getCachesHome() {
      return Paths.get(PathManager.getSystemPath(), "icons");
    }

    @Override
    protected void forkIOTask(@NotNull Runnable action) {
      AppExecutorUtil.getAppExecutorService().execute(action);
    }
  };

  public static final int ICON_DEFAULT_SIZE = 16;

  public static Image load(@NotNull URL url, float scale) throws IOException {
    return load(url, url.openStream(), scale);
  }

  public static Image load(@NotNull InputStream stream, float scale) throws IOException {
    return load(null, stream, scale);
  }

  public static Image load(@Nullable URL url, @NotNull InputStream stream, double scale) throws IOException {
    return load(url, stream, scale, null);
  }

  @ApiStatus.Internal
  public static Image load(@Nullable URL url,
                           @NotNull InputStream stream,
                           double scale,
                           @Nullable ImageLoader.Dimension2DDouble docSize /*OUT*/) throws IOException {
    if (docSize == null) {
      docSize = new ImageLoader.Dimension2DDouble(0, 0);
    }

    byte[] theme = null;
    byte[] svgBytes = null;
    BufferedImage image;

    if (SystemProperties.getBooleanProperty("idea.ui.icons.svg.disk.cache", true)) {
      theme = DEFAULT_THEME;
      SvgElementColorPatcherProvider colorPatcher = ourColorPatcher;
      if (colorPatcher != null) {
        SvgElementColorPatcher subPatcher = colorPatcher.forURL(url);
        if (subPatcher != null) {
          theme = subPatcher.digest();
        }
      }

      if (theme == DEFAULT_THEME) {
        image = SVGLoaderPrebuilt.loadUrlFromPreBuiltCache(url, scale, docSize);
        if (image != null) {
          return image;
        }
      }


      if (theme != null) {
        svgBytes = FileUtil.loadBytes(stream);
        image = ourCache.loadFromCache(theme, svgBytes, scale, docSize);
        if (image != null) {
          return image;
        }
        stream = new ByteArrayInputStream(svgBytes);
      }
    }

    image = loadWithoutCache(url, stream, scale, docSize);
    if (image != null && theme != null) {
      ourCache.storeLoadedImage(theme, svgBytes, scale, image, docSize);
    }
    return image;
  }

  @ApiStatus.Internal
  public static BufferedImage loadWithoutCache(@Nullable URL url, @NotNull InputStream stream, double scale, @Nullable ImageLoader.Dimension2DDouble docSize /*OUT*/) throws IOException {
    try {
      MyTranscoder transcoder = MyTranscoder.createImage(scale, createTranscodeInput(url, stream));
      if (docSize != null) {
        docSize.setSize(transcoder.getOrigDocWidth(), transcoder.getOrigDocHeight());
      }
      return transcoder.getImage();
    }
    catch (TranscoderException ex) {
      if (docSize != null) {
        docSize.setSize(0, 0);
      }
      throw new IOException(ex);
    }
  }

  /**
   * Loads an image with the specified {@code width} and {@code height} (in user space). Size specified in svg file is ignored.
   */
  public static Image load(@Nullable URL url, @NotNull InputStream stream, @NotNull ScaleContext ctx, double width, double height) throws IOException {
    try {
      double s = ctx.getScale(DerivedScaleType.PIX_SCALE);
      return MyTranscoder.createImage(1, createTranscodeInput(url, stream), (float)(width * s), (float)(height * s)).getImage();
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
    BufferedImage image = (BufferedImage)load(url, stream, ctx.getScale(DerivedScaleType.PIX_SCALE));
    @SuppressWarnings("unchecked") T t = (T)ImageUtil.ensureHiDPI(image, ctx);
    return t;
  }

  /** @deprecated Use {@link #loadHiDPI(URL, InputStream, ScaleContext)} */
  @Deprecated
  public static <T extends BufferedImage> T loadHiDPI(@Nullable URL url, @NotNull InputStream stream, JBUI.ScaleContext ctx) throws IOException {
    return loadHiDPI(url, stream, (ScaleContext)ctx);
  }

  public static ImageLoader.Dimension2DDouble getDocumentSize(@Nullable URL url, @NotNull InputStream stream, double scale) throws IOException {
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
        return getDocumentSize(scale, createTranscodeInput(url, new ByteArrayInputStream(buffer.getInternalBuffer(), 0, buffer.size())));
      }
    }
    return new ImageLoader.Dimension2DDouble(ICON_DEFAULT_SIZE * scale, ICON_DEFAULT_SIZE * scale);
  }

  public static double getMaxZoomFactor(@Nullable URL url, @NotNull InputStream stream, @NotNull ScaleContext ctx) throws IOException {
    ImageLoader.Dimension2DDouble size = getDocumentSize(ctx.getScale(DerivedScaleType.PIX_SCALE), createTranscodeInput(url, stream));
    double iconMaxSize = MyTranscoder.getIconMaxSize();
    return Math.min(iconMaxSize / size.getWidth(), iconMaxSize / size.getHeight());
  }

  private SVGLoader() {
  }

  @NotNull
  private static TranscoderInput createTranscodeInput(@Nullable URL url, @NotNull InputStream stream) throws IOException {
    TranscoderInput myTranscoderInput;
    String uri = null;
    try {
      if (url != null && "jar".equals(url.getProtocol())) {
        // workaround for BATIK-1217
        url = new URL(url.getPath());
      }
      uri = url != null ? url.toURI().toString() : null;
    }
    catch (URISyntaxException ignore) { }

    Document document = new SaxSvgDocumentFactory().createDocument(uri, stream);
    patchColors(url, document);
    myTranscoderInput = new TranscoderInput(document);
    return myTranscoderInput;
  }

  private static void patchColors(@Nullable URL url, @NotNull Document document) {
    SvgElementColorPatcherProvider colorPatcher = ourColorPatcher;
    if (colorPatcher != null) {
      final SvgElementColorPatcher patcher = colorPatcher.forURL(url);
      if (patcher != null) {
        patcher.patchColors(document.getDocumentElement());
      }
    }
  }

  /**
   * @deprecated use {@link #setColorPatcherProvider(SvgElementColorPatcherProvider)} instead
   */
  @Deprecated
  public static void setColorPatcher(@Nullable final SvgColorPatcher colorPatcher) {
    if (colorPatcher == null) {
      setColorPatcherProvider(null);
      return;
    }

    setColorPatcherProvider(new SvgElementColorPatcherProvider() {
      @Override
      public SvgElementColorPatcher forURL(@Nullable final URL url) {
        return new SvgElementColorPatcher() {
          @Override
          public void patchColors(@NotNull Element svg) {
            colorPatcher.patchColors(url, svg);
          }

          @Nullable
          @Override
          public byte[] digest() {
            return null;
          }
        };
      }
    });
  }

  public static void setColorPatcherProvider(@Nullable SvgElementColorPatcherProvider colorPatcher) {
    ourColorPatcher = colorPatcher;
    IconLoader.clearCache();
  }

  private static ImageLoader.Dimension2DDouble getDocumentSize(double scale, @NotNull TranscoderInput input) {
    SVGOMDocument document = (SVGOMDocument)input.getDocument();
    BridgeContext ctx = new MyTranscoder(scale).createBridgeContext(document);
    new GVTBuilder().build(ctx, document);
    Dimension2D size = ctx.getDocumentSize();
    return new ImageLoader.Dimension2DDouble(size.getWidth() * scale, size.getHeight() * scale);
  }

  public interface SvgElementColorPatcher {
    void patchColors(@NotNull Element svg);

    /**
     * @return hash code of the current SVG color patcher or null to disable rendered SVG images caching
     */
    @Nullable
    byte[] digest();
  }

  public interface SvgElementColorPatcherProvider {
    @Nullable
    SvgElementColorPatcher forURL(@Nullable URL url);
  }

  /**
   * @deprecated use {@link SvgElementColorPatcherProvider instead}
   */
  @Deprecated
  public interface SvgColorPatcher {

    /**
     * @deprecated use {@link #patchColors(URL, Element)}
     */
    @Deprecated
    default void patchColors(@SuppressWarnings("unused") @NotNull Element svg) {}

    default void patchColors(@Nullable @SuppressWarnings("unused") URL url, @NotNull Element svg) {
      patchColors(svg);
    }
  }
}