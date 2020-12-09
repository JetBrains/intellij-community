// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.ui.icons.IconLoadMeasurer;
import com.intellij.ui.icons.ImageDescriptor;
import com.intellij.ui.scale.DerivedScaleType;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.StartupUiUtil;
import org.imgscalr.Scalr;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageFilter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ImageLoader {
  public static final int ALLOW_FLOAT_SCALING = 1;
  public static final int USE_CACHE = 2;
  public static final int DARK = 4;
  public static final int FIND_SVG = 8;

  private static @NotNull Logger getLogger() {
    return Logger.getInstance(ImageLoader.class);
  }

  public static final long CACHED_IMAGE_MAX_SIZE = (long)(SystemProperties.getFloatProperty("ide.cached.image.max.size", 1.5f) * 1024 * 1024);
  private static final Set<String> IO_MISS_CACHE = Collections.newSetFromMap(new ConcurrentHashMap<>());

  private static final ConcurrentMap<CacheKey, Pair<Image, Dimension2DDouble>> imageCache = CollectionFactory.createConcurrentSoftValueMap();
  // https://github.com/JetBrains/intellij-community/pull/1242
  private static final ConcurrentMap<CacheKey, Image> largeImageCache = CollectionFactory.createConcurrentWeakValueMap();
  private static final ConcurrentMap<Image, ImageLoader.Dimension2DDouble> largeImageDimensionMap = CollectionFactory.createConcurrentWeakMap();

  public static final class Dimension2DDouble {
    private double myWidth;
    private double myHeight;

    public Dimension2DDouble(double width, double height) {
      myWidth = width;
      myHeight = height;
    }

    public void setSize(Dimension2DDouble size) {
      myWidth = size.myWidth;
      myHeight = size.myHeight;
    }

    public void setSize(double width, double height) {
      myWidth = width;
      myHeight = height;
    }

    public double getWidth() {
      return myWidth;
    }

    public double getHeight() {
      return myHeight;
    }
  }

  // @2x is used even for SVG icons by intention
  private static void addFileNameVariant(boolean retina,
                                         boolean isDark,
                                         boolean isSvg,
                                         String name,
                                         String ext,
                                         double scale,
                                         @NotNull List<ImageDescriptor> list) {
    String _ext = isSvg ? "svg" : ext;
    double _scale = isSvg ? scale : (retina ? 2 : 1);

    if (retina && isDark) {
      list.add(new ImageDescriptor(name + "@2x_dark" + "." + _ext, _scale, isSvg, true));
    }
    list.add(new ImageDescriptor(name + (isDark ? "_dark" : "") + (retina ? "@2x" : "") + "." + _ext, _scale, isSvg, isDark));
    if (retina) {
      // a fallback to 1x icon
      list.add(new ImageDescriptor(name + (isDark ? "_dark" : "") + "." + _ext, isSvg ? scale : 1, isSvg, isDark));
    }
  }

  public static void clearCache() {
    imageCache.clear();
    largeImageCache.clear();
    largeImageDimensionMap.clear();
    IO_MISS_CACHE.clear();
  }

  @ApiStatus.Internal
  public static @Nullable Image load(@NotNull String path,
                                     @Nullable List<ImageFilter> filters,
                                     @Nullable Class<?> resourceClass,
                                     @MagicConstant(flagsFromClass = IconLoader.class) int flags,
                                     ScaleContext scaleContext,
                                     boolean isUpScaleNeeded,
                                     long rasterizedCacheKey) {
    long start = StartUpMeasurer.getCurrentTimeIfEnabled();

    List<ImageDescriptor> descriptors = createImageDescriptorList(path, flags, scaleContext);

    boolean ioExceptionThrown = false;
    boolean isHiDpiNeeded = StartupUiUtil.isJreHiDPI(scaleContext);
    for (int i = 0; i < descriptors.size(); i++) {
      ImageDescriptor descriptor = descriptors.get(i);
      try {
        // check only for the first one, as io miss cache doesn't have scale
        Image image = loadByDescriptor(descriptor, BitUtil.isSet(flags, USE_CACHE), resourceClass, i == 0 ? IO_MISS_CACHE : null, path, rasterizedCacheKey);
        if (image == null) {
          continue;
        }
        if (start != -1) {
          IconLoadMeasurer.addLoading(descriptor.isSvg, start);
        }
        return convertImage(image, filters, flags, scaleContext, isUpScaleNeeded, isHiDpiNeeded, descriptor);
      }
      catch (IOException e) {
        ioExceptionThrown = true;
      }
    }

    if (ioExceptionThrown) {
      IO_MISS_CACHE.add(path);
    }
    return null;
  }

  private static @Nullable Image loadByDescriptor(@NotNull ImageDescriptor descriptor,
                                                  boolean useCache,
                                                  @Nullable Class<?> resourceClass,
                                                  @Nullable Set<String> ioMissCache,
                                                  @Nullable String ioMissCacheKey,
                                                  long rasterizedCacheKey) throws IOException {
    CacheKey cacheKey = null;
    if (useCache && !SVGLoader.isSelectionContext()) {
      cacheKey = new CacheKey(descriptor.path, descriptor.isSvg ? descriptor.scale : 0);
      Pair<Image, Dimension2DDouble> pair = imageCache.get(cacheKey);
      if (pair != null) {
        descriptor.originalUserSize.setSize(pair.second);
        return pair.first;
      }

      Image image = largeImageCache.get(cacheKey);
      if (image != null) {
        ImageLoader.Dimension2DDouble dimension = largeImageDimensionMap.get(image);
        if (dimension != null) {
          descriptor.originalUserSize.setSize(dimension);
          return image;
        }
      }
    }

    if (ioMissCache != null && ioMissCache.contains(ioMissCacheKey)) {
      return null;
    }

    Image image;
    long start = StartUpMeasurer.getCurrentTimeIfEnabled();
    if (resourceClass == null) {
      URLConnection connection = new URL(descriptor.path).openConnection();
      if (connection instanceof HttpURLConnection) {
        if (!descriptor.original) {
          return null;
        }
        connection.addRequestProperty("User-Agent", "IntelliJ");
      }

      image = loadFromStream(connection.getInputStream(), descriptor.path, descriptor.scale, descriptor.isDark, descriptor.originalUserSize, descriptor.isSvg);
      if (start != -1) {
        IconLoadMeasurer.addLoadFromUrl(start);
      }
    }
    else {
      if (descriptor.isSvg && SVGLoader.USE_CACHE && !SVGLoader.isSelectionContext()) {
        image = SVGLoader.loadFromClassResource(resourceClass, descriptor.path, rasterizedCacheKey, descriptor.scale, descriptor.isDark, descriptor.originalUserSize);
      }
      else {
        InputStream stream = resourceClass.getResourceAsStream(descriptor.path);
        if (stream == null) {
          return null;
        }
        image = loadFromStream(stream, null, descriptor.scale, descriptor.isDark, descriptor.originalUserSize, descriptor.isSvg);
      }
      if (start != -1) {
        IconLoadMeasurer.addLoadFromResources(start);
      }
    }

    if (cacheKey != null && image != null) {
      if (4L * image.getWidth(null) * image.getHeight(null) <= CACHED_IMAGE_MAX_SIZE) {
        imageCache.put(cacheKey, new Pair<>(image, descriptor.originalUserSize));
      }
      else {
        largeImageCache.put(cacheKey, image);
        largeImageDimensionMap.put(image, descriptor.originalUserSize);
      }
    }
    return image;
  }

  public static @NotNull Image loadFromStream(@NotNull InputStream stream,
                                              @Nullable String path,
                                              double scale,
                                              boolean isDark,
                                              @NotNull ImageLoader.Dimension2DDouble originalUserSize,
                                              boolean isSvg) throws IOException {
    try {
      if (isSvg) {
        return SVGLoader.load(path, stream, scale, isDark, originalUserSize);
      }

      long start = StartUpMeasurer.getCurrentTimeIfEnabled();
      Image image;
      if (stream instanceof BufferExposingByteArrayInputStream) {
        BufferExposingByteArrayInputStream byteInput = (BufferExposingByteArrayInputStream)stream;
        image = Toolkit.getDefaultToolkit().createImage(byteInput.getInternalBuffer(), 0, byteInput.available());
      }
      else {
        BufferExposingByteArrayOutputStream outputStream = new BufferExposingByteArrayOutputStream();
        try {
          FileUtilRt.copy(stream, outputStream);
        }
        finally {
          stream.close();
        }
        image = Toolkit.getDefaultToolkit().createImage(outputStream.getInternalBuffer(), 0, outputStream.size());
      }

      waitForImage(image);
      originalUserSize.setSize(image.getWidth(null) / scale, image.getHeight(null) / scale);
      if (start != -1) {
        IconLoadMeasurer.pngDecoding.addDurationStartedAt(start);
      }
      return image;
    }
    finally {
      stream.close();
    }
  }

  private static void waitForImage(@NotNull Image image) {
    if (image.getWidth(null) > 0) {
      return;
    }
    MediaTracker mediatracker = new MediaTracker(ImageLoader.ourComponent);
    mediatracker.addImage(image, 1);
    try {
      mediatracker.waitForID(1, 5000);
    }
    catch (InterruptedException ex) {
      getLogger().info(ex);
    }
  }

  private static @Nullable Image convertImage(@NotNull Image image,
                                              @Nullable List<ImageFilter> filters,
                                              @MagicConstant(flagsFromClass = IconLoader.class) int flags,
                                              ScaleContext scaleContext,
                                              boolean isUpScaleNeeded,
                                              boolean isHiDpiNeeded,
                                              ImageDescriptor descriptor) {
    if (isUpScaleNeeded && !descriptor.isSvg) {
      double scale = adjustScaleFactor(BitUtil.isSet(flags, ALLOW_FLOAT_SCALING), scaleContext.getScale(DerivedScaleType.PIX_SCALE));
      if (descriptor.scale > 1) {
        // compensate the image original scale
        scale /= descriptor.scale;
      }
      image = scaleImage(image, scale);
    }

    if (filters != null && !filters.isEmpty()) {
      Toolkit toolkit = Toolkit.getDefaultToolkit();
      for (ImageFilter filter : filters) {
        if (filter != null) {
          image = toolkit.createImage(new FilteredImageSource(ImageUtil.toBufferedImage(image, false).getSource(), filter));
        }
      }
    }

    if (isHiDpiNeeded) {
      double userScale = scaleContext.getScale(DerivedScaleType.EFF_USR_SCALE);
      image = new JBHiDPIScaledImage(image, descriptor.originalUserSize.getWidth() * userScale,
                                     descriptor.originalUserSize.getHeight() * userScale, BufferedImage.TYPE_INT_ARGB);
    }
    return image;
  }

  public static @NotNull List<ImageDescriptor> getImageDescriptors(@NotNull String path,
                                                                   @MagicConstant(flagsFromClass = ImageLoader.class) int flags,
                                                                   @NotNull ScaleContext scaleContext) {
    return createImageDescriptorList(path, flags, scaleContext);
  }

  private static List<ImageDescriptor> createImageDescriptorList(@NotNull String path,
                                                                 @MagicConstant(flagsFromClass = ImageLoader.class) int flags,
                                                                 @NotNull ScaleContext scaleContext) {
    // Prefer retina images for HiDPI scale, because downscaling
    // retina images provides a better result than up-scaling non-retina images.
    double pixScale = scaleContext.getScale(DerivedScaleType.PIX_SCALE);

    int i = path.lastIndexOf('.');
    final String name = i < 0 ? path : path.substring(0, i);
    String ext = i < 0 || (i == path.length() - 1) ? "" : path.substring(i + 1);
    double scale = adjustScaleFactor(BitUtil.isSet(flags, ALLOW_FLOAT_SCALING), pixScale);

    List<ImageDescriptor> list;
    if (!path.startsWith("file:") && path.contains("://")) {
      boolean isSvg = StringUtilRt.endsWithIgnoreCase(StringUtil.substringBeforeLast(path, "?"), ".svg");
      list = Collections.singletonList(new ImageDescriptor(name + "." + ext, 1.0, isSvg, true));
    }
    else {
      boolean isSvg = "svg".equalsIgnoreCase(ext);
      boolean isDark = BitUtil.isSet(flags, DARK);
      boolean retina = JBUIScale.isHiDPI(pixScale);

      list = new ArrayList<>();
      if (!isSvg && BitUtil.isSet(flags, FIND_SVG)) {
        addFileNameVariant(retina, isDark, true, name, ext, scale, list);
      }
      addFileNameVariant(retina, isDark, isSvg, name, ext, scale, list);
      if (isDark) {
        // fallback to non-dark
        addFileNameVariant(retina, false, isSvg, name, ext, scale, list);
        if (!isSvg && BitUtil.isSet(flags, FIND_SVG)) {
          addFileNameVariant(false, false, true, name, ext, scale, list);
        }
      }
    }
    return list;
  }

  public static final Component ourComponent = new Component() {
  };

  public static @Nullable Image loadFromUrl(@NotNull URL url) {
    return loadFromUrl(url, true);
  }

  public static @Nullable Image loadFromUrl(@NotNull URL url, boolean allowFloatScaling) {
    return loadFromUrl(url, allowFloatScaling, true, null, ScaleContext.create());
  }

  public static @Nullable Image loadFromUrl(@NotNull URL url,
                                            boolean allowFloatScaling,
                                            boolean useCache,
                                            @Nullable List<ImageFilter> filters,
                                            @NotNull ScaleContext ctx) {
    int flags = FIND_SVG;
    if (allowFloatScaling) {
      flags |= ALLOW_FLOAT_SCALING;
    }
    if (useCache) {
      flags |= USE_CACHE;
    }
    if (StartupUiUtil.isUnderDarcula()) {
      flags |= DARK;
    }
    return loadFromUrl(url.toString(), null, flags, filters, ctx);
  }

  /**
   * Loads an image of available resolution (1x, 2x, ...) and scales to address the provided scale context.
   * Then wraps the image with {@link JBHiDPIScaledImage} if necessary.
   */
  public static @Nullable Image loadFromUrl(@NotNull String path,
                                            @Nullable Class<?> aClass,
                                            @MagicConstant(flagsFromClass = IconLoader.class) int flags,
                                            @Nullable List<ImageFilter> filters,
                                            @NotNull ScaleContext scaleContext) {
    // We can't check all 3rd party plugins and convince the authors to add @2x icons.
    // In IDE-managed HiDPI mode with scale > 1.0 we scale images manually - pass isUpScaleNeeded = true
    return load(path, filters, aClass, flags, scaleContext, !path.endsWith(".svg"), 0);
  }

  private static double adjustScaleFactor(boolean allowFloatScaling, double scale) {
    return allowFloatScaling ? scale : JBUIScale.isHiDPI(scale) ? 2f : 1f;
  }

  public static @NotNull Image scaleImage(@NotNull Image image, double scale) {
    if (scale == 1.0) {
      return image;
    }

    if (image instanceof JBHiDPIScaledImage) {
      return ((JBHiDPIScaledImage)image).scale(scale);
    }

    int w = image.getWidth(null);
    int h = image.getHeight(null);
    if (w <= 0 || h <= 0) {
      return image;
    }
    int width = (int)Math.round(scale * w);
    int height = (int)Math.round(scale * h);
    // Using "QUALITY" instead of "ULTRA_QUALITY" results in images that are less blurry
    // because ultra quality performs a few more passes when scaling, which introduces blurriness
    // when the scaling factor is relatively small (i.e. <= 3.0f) -- which is the case here.
    return Scalr.resize(ImageUtil.toBufferedImage(image, false), Scalr.Method.QUALITY, Scalr.Mode.FIT_EXACT, width, height, (BufferedImageOp[])null);
  }

  @NotNull
  public static Image scaleImage(@NotNull Image image, int targetSize) {
    return scaleImage(image, targetSize, targetSize);
  }

  @NotNull
  public static Image scaleImage(@NotNull Image image, int targetWidth, int targetHeight) {
    if (image instanceof JBHiDPIScaledImage) {
      return ((JBHiDPIScaledImage)image).scale(targetWidth, targetHeight);
    }
    int w = image.getWidth(null);
    int h = image.getHeight(null);

    if (w <= 0 || h <= 0 || w == targetWidth && h == targetHeight) {
      return image;
    }

    return Scalr.resize(ImageUtil.toBufferedImage(image), Scalr.Method.QUALITY, Scalr.Mode.FIT_EXACT,
                        targetWidth, targetHeight,
                        (BufferedImageOp[])null);
  }

  /**
   * @deprecated Use {@link #loadFromResource(String, Class)}
   */
  @Deprecated
  public static @Nullable Image loadFromResource(@NonNls @NotNull String s) {
    Class<?> callerClass = ReflectionUtil.getGrandCallerClass();
    return callerClass == null ? null : loadFromResource(s, callerClass);
  }

  public static @Nullable Image loadFromResource(@NonNls @NotNull String path, @NotNull Class<?> aClass) {
    ScaleContext scaleContext = ScaleContext.create();
    int flags = FIND_SVG | ALLOW_FLOAT_SCALING | USE_CACHE;
    if (StartupUiUtil.isUnderDarcula()) {
      flags |= DARK;
    }
    return load(path, null, aClass, flags, scaleContext, false, 0);
  }

  public static Image loadFromBytes(byte @NotNull [] bytes) {
    return loadFromStream(new ByteArrayInputStream(bytes));
  }

  public static Image loadFromStream(@NotNull InputStream inputStream) {
    // for backward compatibility assume the image is hidpi-aware (includes default SYS_SCALE)
    ScaleContext scaleContext = ScaleContext.create();
    try {
      ImageLoader.Dimension2DDouble originalUserSize = new ImageLoader.Dimension2DDouble(0, 0);
      Image image = loadFromStream(inputStream, null, scaleContext.getScale(DerivedScaleType.PIX_SCALE), false, originalUserSize, false);
      if (StartupUiUtil.isJreHiDPI(scaleContext)) {
        double userScale = scaleContext.getScale(DerivedScaleType.EFF_USR_SCALE);
        image = new JBHiDPIScaledImage(image, originalUserSize.getWidth() * userScale, originalUserSize.getHeight() * userScale,
                                       BufferedImage.TYPE_INT_ARGB);
      }
      return image;
    }
    catch (IOException e) {
      getLogger().error(e);
    }
    return null;
  }

  public static @Nullable Image loadCustomIcon(@NotNull File file) throws IOException {
    ScaleContext scaleContext = ScaleContext.create();
    // probably, need implement naming conventions: filename ends with @2x => HiDPI (scale=2)
    double scale = scaleContext.getScale(DerivedScaleType.PIX_SCALE);
    ImageDescriptor imageDescriptor = new ImageDescriptor(file.toURI().toURL().toString(), scale, StringUtilRt.endsWithIgnoreCase(file.getPath(), ".svg"), file.getPath().contains("_dark."));
    Image icon = ImageUtil.ensureHiDPI(loadByDescriptor(imageDescriptor, true, null, null, null, 0), scaleContext);
    if (icon == null) {
      return null;
    }

    int w = icon.getWidth(null);
    int h = icon.getHeight(null);
    if (w <= 0 || h <= 0) {
      getLogger().error("negative image size: w=" + w + ", h=" + h + ", path=" + file.getPath());
      return null;
    }

    if (w > EmptyIcon.ICON_18.getIconWidth() || h > EmptyIcon.ICON_18.getIconHeight()) {
      double s = EmptyIcon.ICON_18.getIconWidth()/(double)Math.max(w, h);
      return scaleImage(icon, s);
    }

    return icon;
  }
}

final class CacheKey {
  private final String path;
  private final double scale;

  CacheKey(@NotNull String path, double scale) {
    this.path = path;
    this.scale = scale;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    CacheKey key = (CacheKey)o;
    return key.scale == scale && path.equals(key.path);
  }

  @Override
  public int hashCode() {
    long temp = Double.doubleToLongBits(scale);
    return 31 * path.hashCode() + (int)(temp ^ (temp >>> 32));
  }
}