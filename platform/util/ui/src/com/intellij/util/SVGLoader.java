// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.Strings;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.icons.IconLoadMeasurer;
import com.intellij.ui.scale.DerivedScaleType;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.ui.svg.SvgCacheManager;
import com.intellij.ui.svg.SvgDocumentFactoryKt;
import com.intellij.ui.svg.SvgPrebuiltCacheManager;
import com.intellij.ui.svg.SvgTranscoder;
import com.intellij.util.ui.ImageUtil;
import org.apache.batik.transcoder.TranscoderException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@ApiStatus.Internal
public final class SVGLoader {
  private static final byte[] DEFAULT_THEME = ArrayUtilRt.EMPTY_BYTE_ARRAY;
  private static final boolean USE_CACHE = Boolean.parseBoolean(System.getProperty("idea.ui.icons.svg.disk.cache", "true"));

  private static SvgElementColorPatcherProvider ourColorPatcher;
  private static SvgElementColorPatcherProvider ourColorPatcherForSelection;

  private static volatile boolean ourIsSelectionContext = false;

  private static final class SvgCache {
    private static final SvgCacheManager persistentCache;
    private static final SvgPrebuiltCacheManager prebuiltPersistentCache;

    static {
      SvgPrebuiltCacheManager prebuiltCache;
      try {
        Path dbFile = null;
        if (USE_CACHE) {
          String dbPath = System.getProperty("idea.ui.icons.prebuilt.db");
          if (!"false".equals(dbPath)) {
            if (dbPath == null || dbPath.isEmpty()) {
              dbFile = Path.of(PathManager.getBinPath() + "/icons.db");
            }
            else {
              dbFile = Path.of(dbPath);
            }
          }
        }

        prebuiltCache = dbFile != null && Files.exists(dbFile) ? new SvgPrebuiltCacheManager(dbFile) : null;
      }
      catch (Exception e) {
        Logger.getInstance(SVGLoader.class).error("Cannot use prebuilt svg cache", e);
        prebuiltCache = null;
      }

      prebuiltPersistentCache = prebuiltCache;

      SvgCacheManager cache;
      try {
        cache = USE_CACHE ? new SvgCacheManager(Path.of(PathManager.getSystemPath(), "icons-v3.db")) : null;
      }
      catch (Exception e) {
        Logger.getInstance(SVGLoader.class).error(e);
        cache = null;
      }

      persistentCache = cache;
    }
  }

  public static @Nullable SvgCacheManager getCache() {
    return SvgCache.persistentCache;
  }

  public static final int ICON_DEFAULT_SIZE = 16;

  private SVGLoader() {
  }

  public static Image load(@NotNull URL url, float scale) throws IOException {
    return load(url.getPath(), url.openStream(), scale, false, null);
  }

  public static Image load(@NotNull InputStream stream, float scale) throws IOException {
    return load(null, stream, scale, false, null);
  }

  public static Image load(@Nullable URL url, @NotNull InputStream stream, float scale) throws IOException {
    return load(url == null ? null : url.getPath(), stream, scale, false, null);
  }

  @ApiStatus.Internal
  public static @Nullable Image loadFromClassResource(@Nullable Class<?> resourceClass,
                                                      @Nullable ClassLoader classLoader,
                                                      @NotNull String path,
                                                      long rasterizedCacheKey,
                                                      float scale,
                                                      boolean isDark,
                                                      @NotNull ImageLoader.Dimension2DDouble docSize /*OUT*/) throws IOException {
    byte[] svgBytes = null;
    byte[] theme ;
    InputStream stream = null;

    if (USE_CACHE && !isSelectionContext()) {
      @SuppressWarnings("DuplicatedCode")
      long start = StartUpMeasurer.getCurrentTimeIfEnabled();

      theme = DEFAULT_THEME;

      SvgElementColorPatcherProvider colorPatcher = ourColorPatcher;
      if (colorPatcher != null) {
        SvgElementColorPatcher subPatcher = colorPatcher.forPath(path);
        if (subPatcher != null) {
          theme = subPatcher.digest();
        }
      }

      if (theme != null) {
        Image image;
        if (theme == DEFAULT_THEME && rasterizedCacheKey != 0) {
          SvgPrebuiltCacheManager cache = SvgCache.prebuiltPersistentCache;
          if (cache != null) {
            image = cache.loadFromCache(rasterizedCacheKey, scale, isDark, docSize);
            if (image != null) {
              return image;
            }
          }
        }

        stream = ImageLoader.getResourceData(path, resourceClass, classLoader);
        if (stream == null) {
          return null;
        }
        try {
          svgBytes = stream.readAllBytes();
        }
        finally {
          stream.close();
        }

        image = SvgCache.persistentCache.loadFromCache(theme, svgBytes, scale, isDark, docSize);
        if (image != null) {
          return image;
        }

        stream = new ByteArrayInputStream(svgBytes);
      }

      if (start != -1) {
        IconLoadMeasurer.svgCacheRead.end(start);
      }
    }
    else {
      theme = null;
    }

    if (stream == null) {
      stream = ImageLoader.getResourceData(path, resourceClass, classLoader);
      if (stream == null) {
        return null;
      }
    }
    return loadAndCache(path, stream, scale, docSize, theme, svgBytes);
  }

  @ApiStatus.Internal
  public static @NotNull Image load(@Nullable String path,
                                    @NotNull InputStream stream,
                                    float scale,
                                    boolean isDark,
                                    @Nullable ImageLoader.Dimension2DDouble docSize /*OUT*/) throws IOException {
    if (docSize == null) {
      docSize = new ImageLoader.Dimension2DDouble(0, 0);
    }

    byte[] theme = null;
    byte[] svgBytes = null;
    Image image;

    if (USE_CACHE && !isSelectionContext()) {
      long start = StartUpMeasurer.getCurrentTimeIfEnabled();
      theme = DEFAULT_THEME;
      SvgElementColorPatcherProvider colorPatcher = ourColorPatcher;
      if (colorPatcher != null) {
        SvgElementColorPatcher subPatcher = colorPatcher.forPath(path);
        if (subPatcher != null) {
          theme = subPatcher.digest();
        }
      }

      if (theme != null) {
        svgBytes = stream.readAllBytes();
        image = SvgCache.persistentCache.loadFromCache(theme, svgBytes, scale, isDark, docSize);
        if (image != null) {
          return image;
        }
        stream = new ByteArrayInputStream(svgBytes);
      }

      if (start != -1) {
        IconLoadMeasurer.svgCacheRead.end(start);
      }
    }
    return loadAndCache(path, stream, scale, docSize, theme, svgBytes);
  }

  private static @NotNull BufferedImage loadAndCache(@Nullable String path,
                                                     @NotNull InputStream stream,
                                                     float scale,
                                                     @NotNull ImageLoader.Dimension2DDouble docSize,
                                                     byte[] theme,
                                                     byte[] svgBytes) throws IOException {
    long decodingStart = StartUpMeasurer.getCurrentTimeIfEnabled();
    BufferedImage bufferedImage;
    try {
      bufferedImage = SvgTranscoder.createImage(scale, createDocument(path, stream), docSize);
    }
    catch (TranscoderException e) {
      docSize.setSize(0, 0);
      throw new IOException(e);
    }

    if (decodingStart != -1) {
      IconLoadMeasurer.svgDecoding.end(decodingStart);
    }

    if (theme != null) {
      try {
        long cacheWriteStart = StartUpMeasurer.getCurrentTimeIfEnabled();
        SvgCache.persistentCache.storeLoadedImage(theme, svgBytes, scale, bufferedImage, docSize);
        IconLoadMeasurer.svgCacheWrite.end(cacheWriteStart);
      }
      catch (Exception e) {
        Logger.getInstance(SVGLoader.class).error("Failed to write SVG cache for: " + path, e);
      }
    }
    return bufferedImage;
  }

  public static @NotNull BufferedImage loadWithoutCache(byte @NotNull [] content, float scale) throws IOException {
    try {
      return SvgTranscoder.createImage(scale, createDocument(null, new ByteArrayInputStream(content)), null);
    }
    catch (TranscoderException e) {
      throw new IOException(e);
    }
  }

  /**
   * Loads an image with the specified {@code width} and {@code height} (in user space). Size specified in svg file is ignored.
   */
  public static Image load(@Nullable URL url, @NotNull InputStream stream, @NotNull ScaleContext scaleContext, double width, double height) throws IOException {
    try {
      double scale = scaleContext.getScale(DerivedScaleType.PIX_SCALE);
      return SvgTranscoder
        .createImage(1, createDocument(url != null ? url.getPath() : null, stream), null, (float)(width * scale), (float)(height * scale));
    }
    catch (TranscoderException e) {
      throw new IOException(e);
    }
  }

  /**
   * Loads a HiDPI-aware image of the size specified in the svg file.
   */
  public static <T extends BufferedImage> T loadHiDPI(@Nullable URL url, @NotNull InputStream stream, ScaleContext context) throws IOException {
    BufferedImage image = (BufferedImage)load(url == null ? null : url.getPath(), stream, (float)context.getScale(DerivedScaleType.PIX_SCALE), false, null);
    @SuppressWarnings("unchecked") T t = (T)ImageUtil.ensureHiDPI(image, context);
    return t;
  }

  public static ImageLoader.Dimension2DDouble getDocumentSize(@NotNull InputStream stream, float scale) throws IOException {
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
        ByteArrayInputStream input = new ByteArrayInputStream(buffer.getInternalBuffer(), 0, buffer.size());
        return SvgTranscoder.getDocumentSize(scale, SvgDocumentFactoryKt.createSvgDocument(null, input));
      }
    }
    return new ImageLoader.Dimension2DDouble(ICON_DEFAULT_SIZE * scale, ICON_DEFAULT_SIZE * scale);
  }

  public static double getMaxZoomFactor(@Nullable String path, @NotNull InputStream stream, @NotNull ScaleContext scaleContext) throws IOException {
    ImageLoader.Dimension2DDouble size = SvgTranscoder.getDocumentSize((float)scaleContext.getScale(DerivedScaleType.PIX_SCALE), createDocument(path, stream));
    float iconMaxSize = SvgTranscoder.getIconMaxSize();
    return Math.min(iconMaxSize / size.getWidth(), iconMaxSize / size.getHeight());
  }

  private static @NotNull Document createDocument(@Nullable String url, @NotNull InputStream inputStream) {
    Document document = SvgDocumentFactoryKt.createSvgDocument(url, inputStream);
    patchColors(url, document);
    return document;
  }

  private static void patchColors(@Nullable String url, @NotNull Document document) {
    SvgElementColorPatcherProvider colorPatcher = ourColorPatcher;
    if (colorPatcher != null) {
      SvgElementColorPatcher patcher = colorPatcher.forPath(url);
      if (patcher != null) {
        patcher.patchColors(document.getDocumentElement());
      }
    }
    if (isSelectionContext()) {
      SvgElementColorPatcherProvider selectionPatcherProvider = getSelectionPatcherProvider();
      if (selectionPatcherProvider != null) {
        SvgElementColorPatcher selectionPatcher = selectionPatcherProvider.forPath(url);
        if (selectionPatcher != null) {
          selectionPatcher.patchColors(document.getDocumentElement());
        }
      }
    }
  }

  public static void setColorPatcherForSelection(@Nullable SvgElementColorPatcherProvider provider) {
    ourColorPatcherForSelection = provider;
  }

  private static SvgElementColorPatcherProvider getSelectionPatcherProvider() {
    //todo[kb] move this code to a common place for LaFs and themes.
    //HashMap<String, String> map = new HashMap<>();
    //map.put("#f26522", "#e2987c");
    //HashMap<String, Integer> alpha = new HashMap<>();
    //alpha.put("#e2987c", 255);
    //
    //return newPatcher(null, map, alpha);
    return ourColorPatcherForSelection;
  }

  @Nullable
  public static SVGLoader.SvgElementColorPatcher newPatcher(byte @Nullable [] digest,
                                                            @NotNull Map<String, String> newPalette,
                                                            @NotNull Map<String, Integer> alphas) {
    if (newPalette.isEmpty()) {
      return null;
    }

    return new SVGLoader.SvgElementColorPatcher() {
      @Override
      public byte[] digest() {
        return digest;
      }

      @Override
      public void patchColors(@NotNull Element svg) {
        patchColorAttribute(svg, "fill");
        patchColorAttribute(svg, "stroke");
        NodeList nodes = svg.getChildNodes();
        int length = nodes.getLength();
        for (int i = 0; i < length; i++) {
          Node item = nodes.item(i);
          if (item instanceof Element) {
            patchColors((Element)item);
          }
        }
      }

      private void patchColorAttribute(@NotNull Element svg, String attrName) {
        String color = svg.getAttribute(attrName);
        String opacity = svg.getAttribute(attrName + "-opacity");
        if (!Strings.isEmpty(color)) {
          int alpha = 255;
          try {
            alpha = (int)Math.ceil(255f * Float.valueOf(opacity));
          }catch (Exception ignore){}
          String newColor = null;
          String key = toCanonicalColor(color);
          if (alpha != 255) {
            newColor = newPalette.get(key + Integer.toHexString(alpha));
          }
          if (newColor == null) {
            newColor = newPalette.get(key);
          }

          if (newColor != null) {
            svg.setAttribute(attrName, newColor);
            if (alphas.get(newColor) != null) {
              svg.setAttribute(attrName + "-opacity", String.valueOf((Float.valueOf(alphas.get(newColor)) / 255f)));
            }
          }
        }
      }
    };
  }

  private static String toCanonicalColor(String color) {
    String s = Strings.toLowerCase(color);
    //todo[kb]: add support for red, white, black, and other named colors
    if (s.startsWith("#") && s.length() < 7) {
      s = "#" + ColorUtil.toHex(ColorUtil.fromHex(s));
    }
    return s;
  }

  public static void setColorPatcherProvider(@Nullable SvgElementColorPatcherProvider colorPatcher) {
    ourColorPatcher = colorPatcher;
    IconLoader.clearCache();
  }

  public static void setIsSelectionContext(boolean isSelectionContext) {
    ourIsSelectionContext = isSelectionContext;
  }

  public static boolean isSelectionContext() {
    return ourColorPatcherForSelection != null
           && EventQueue.isDispatchThread()
           && ourIsSelectionContext
           && Registry.is("ide.patch.icons.on.selection", false);
  }

  public static void paintIconWithSelection(Icon icon, Component c, Graphics g, int x, int y) {
    try {
      setIsSelectionContext(true);
      icon.paintIcon(c, g, x, y);
    }
    finally {
      setIsSelectionContext(false);
    }
  }

  public interface SvgElementColorPatcher {
    void patchColors(@NotNull Element svg);

    /**
     * @return hash code of the current SVG color patcher or null to disable rendered SVG images caching
     */
    byte @Nullable [] digest();
  }

  public interface SvgElementColorPatcherProvider {
    /**
     * @deprecated Use {@link #forPath(String)}
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    default @Nullable SvgElementColorPatcher forURL(@SuppressWarnings("unused") @Nullable URL url) {
      return null;
    }

    default @Nullable SvgElementColorPatcher forPath(@Nullable String path) {
      return forURL(null);
    }
  }
}