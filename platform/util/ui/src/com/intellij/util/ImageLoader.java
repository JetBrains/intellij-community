// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream;
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

import javax.imageio.ImageIO;
import javax.swing.*;
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
  public static final int ALLOW_FLOAT_SCALING = 0x01;
  public static final int USE_CACHE           = 0x02;
  public static final int USE_DARK            = 0x04;
  public static final int USE_SVG             = 0x08;
  public static final int USE_IMAGE_IO        = 0x10;

  private static @NotNull Logger getLogger() {
    return Logger.getInstance(ImageLoader.class);
  }

  private static final long CACHED_IMAGE_MAX_SIZE = (long)(SystemProperties.getFloatProperty("ide.cached.image.max.size", 1.5f) * 1024 * 1024);
  private static final Set<String> IO_MISS_CACHE = Collections.newSetFromMap(new ConcurrentHashMap<>());

  private static final ConcurrentMap<CacheKey, Pair<Image, Dimension2DDouble>> imageCache = CollectionFactory.createConcurrentSoftValueMap();
  // https://github.com/JetBrains/intellij-community/pull/1242
  private static final ConcurrentMap<CacheKey, Image> largeImageCache = CollectionFactory.createConcurrentWeakValueMap();
  private static final ConcurrentMap<Image, ImageLoader.Dimension2DDouble> largeImageDimensionMap = CollectionFactory.createConcurrentWeakMap();

  @ApiStatus.Internal
  public static boolean isIconTooLargeForCache(@NotNull Icon icon) {
    return 4L * icon.getIconWidth() * icon.getIconHeight() > CACHED_IMAGE_MAX_SIZE;
  }

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
                                         float scale,
                                         @NotNull List<ImageDescriptor> list) {
    String _ext = isSvg ? "svg" : ext;
    float _scale = isSvg ? scale : (retina ? 2 : 1);

    if (retina && isDark) {
      list.add(new ImageDescriptor(name + "@2x_dark." + _ext, _scale, isSvg, true));
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
  public static @Nullable Image loadRasterized(@NotNull String path,
                                               @Nullable List<ImageFilter> filters,
                                               @NotNull Class<?> resourceClass,
                                               @MagicConstant(flagsFromClass = ImageLoader.class) int flags,
                                               ScaleContext scaleContext,
                                               boolean isUpScaleNeeded,
                                               long rasterizedCacheKey,
                                               @MagicConstant(flagsFromClass = ImageDescriptor.class) int imageFlags) {
    long loadingStart = StartUpMeasurer.getCurrentTimeIfEnabled();

    // Prefer retina images for HiDPI scale, because downscaling
    // retina images provides a better result than up-scaling non-retina images.
    float pixScale = (float)scaleContext.getScale(DerivedScaleType.PIX_SCALE);

    int dotIndex = path.lastIndexOf('.');
    String name = dotIndex < 0 ? path : path.substring(0, dotIndex);
    float scale = adjustScaleFactor(BitUtil.isSet(flags, ALLOW_FLOAT_SCALING), pixScale);

    boolean isSvg = rasterizedCacheKey != 0;
    boolean isDark = BitUtil.isSet(flags, USE_DARK)   ;
    boolean isRetina = JBUIScale.isHiDPI(pixScale);

    float imageScale;

    String ext = isSvg ? "svg" : (dotIndex < 0 || (dotIndex == path.length() - 1) ? "" : path.substring(dotIndex + 1));

    String effectivePath;
    boolean isEffectiveDark = isDark;
    if (isRetina && isDark && (imageFlags & ImageDescriptor.HAS_DARK_2x) == ImageDescriptor.HAS_DARK_2x) {
      effectivePath = name + "@2x_dark." + ext;
      imageScale = isSvg ? scale : 2;
    }
    else if (isDark && (imageFlags & ImageDescriptor.HAS_DARK) == ImageDescriptor.HAS_DARK) {
      effectivePath = name + "_dark." + ext;
      imageScale = isSvg ? scale : 1;
    }
    else {
      isEffectiveDark = false;
      if (isRetina && (imageFlags & ImageDescriptor.HAS_2x) == ImageDescriptor.HAS_2x) {
        effectivePath = name + "@2x." + ext;
        imageScale = isSvg ? scale : 2;
      }
      else {
        effectivePath = path;
        imageScale = isSvg ? scale : 1;
      }
    }

    Dimension2DDouble originalUserSize = new Dimension2DDouble(0, 0);
    try {
      long start = StartUpMeasurer.getCurrentTimeIfEnabled();
      Image image;
      if (isSvg) {
        image = SVGLoader.loadFromClassResource(resourceClass, effectivePath, rasterizedCacheKey, imageScale, isEffectiveDark, originalUserSize);
      }
      else {
        image = loadPngFromClassResource(effectivePath, resourceClass, imageScale, originalUserSize, BitUtil.isSet(flags, USE_IMAGE_IO));
      }

      if (start != -1) {
        IconLoadMeasurer.addLoadFromResources(start);
      }
      if (loadingStart != -1) {
        IconLoadMeasurer.addLoading(isSvg, loadingStart);
      }

      if (image == null) {
        return null;
      }
      return convertImage(image, filters, flags, scaleContext, isUpScaleNeeded, StartupUiUtil.isJreHiDPI(scaleContext), imageScale, isSvg, originalUserSize);
    }
    catch (IOException e) {
      getLogger().debug(e);
      return null;
    }
  }

  @ApiStatus.Internal
  public static @Nullable Image load(@NotNull String path,
                                     @Nullable List<ImageFilter> filters,
                                     @Nullable Class<?> resourceClass,
                                     @MagicConstant(flagsFromClass = ImageLoader.class) int flags,
                                     ScaleContext scaleContext,
                                     boolean isUpScaleNeeded) {
    long start = StartUpMeasurer.getCurrentTimeIfEnabled();

    List<ImageDescriptor> descriptors = createImageDescriptorList(path, flags, scaleContext);

    boolean ioExceptionThrown = false;
    boolean isHiDpiNeeded = StartupUiUtil.isJreHiDPI(scaleContext);
    ImageLoader.Dimension2DDouble originalUserSize = new Dimension2DDouble(0, 0);
    for (int i = 0; i < descriptors.size(); i++) {
      ImageDescriptor descriptor = descriptors.get(i);
      try {
        // check only for the first one, as io miss cache doesn't have scale
        Image image = loadByDescriptor(descriptor, flags, resourceClass, originalUserSize, i == 0 ? IO_MISS_CACHE : null, path);
        if (image == null) {
          continue;
        }
        if (start != -1) {
          IconLoadMeasurer.addLoading(descriptor.isSvg, start);
        }
        return convertImage(image, filters, flags, scaleContext, isUpScaleNeeded, isHiDpiNeeded, descriptor.scale, descriptor.isSvg, originalUserSize);
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
                                                  @MagicConstant(flags = {USE_CACHE, USE_IMAGE_IO}) int flags,
                                                  @Nullable Class<?> resourceClass,
                                                  @NotNull ImageLoader.Dimension2DDouble originalUserSize,
                                                  @Nullable Set<String> ioMissCache,
                                                  @Nullable String ioMissCacheKey) throws IOException {
    CacheKey cacheKey = null;
    if (BitUtil.isSet(flags, USE_CACHE) && !SVGLoader.isSelectionContext()) {
      cacheKey = new CacheKey(descriptor.path, descriptor.isSvg ? descriptor.scale : 0);
      Pair<Image, Dimension2DDouble> pair = imageCache.get(cacheKey);
      if (pair != null) {
        originalUserSize.setSize(pair.second);
        return pair.first;
      }

      Image image = largeImageCache.get(cacheKey);
      if (image != null) {
        ImageLoader.Dimension2DDouble dimension = largeImageDimensionMap.get(image);
        if (dimension != null) {
          originalUserSize.setSize(dimension);
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

      try (InputStream stream = connection.getInputStream()) {
        if (descriptor.isSvg) {
          image = SVGLoader.load(descriptor.path, stream, descriptor.scale, descriptor.isDark, originalUserSize);
        }
        else {
          image = loadPng(stream, descriptor.scale, originalUserSize, BitUtil.isSet(flags, USE_IMAGE_IO));
        }
      }
      if (start != -1) {
        IconLoadMeasurer.addLoadFromUrl(start);
      }
    }
    else {
      if (descriptor.isSvg) {
        image = SVGLoader.loadFromClassResource(resourceClass, descriptor.path, 0, descriptor.scale, descriptor.isDark, originalUserSize);
      }
      else {
        image = loadPngFromClassResource(descriptor.path, resourceClass, descriptor.scale, originalUserSize, BitUtil.isSet(flags, USE_IMAGE_IO));
      }
      if (start != -1) {
        IconLoadMeasurer.addLoadFromResources(start);
      }
    }

    if (cacheKey != null && image != null) {
      if (4L * image.getWidth(null) * image.getHeight(null) <= CACHED_IMAGE_MAX_SIZE) {
        imageCache.put(cacheKey, new Pair<>(image, originalUserSize));
      }
      else {
        largeImageCache.put(cacheKey, image);
        largeImageDimensionMap.put(image, originalUserSize);
      }
    }
    return image;
  }

  private static @Nullable Image loadPngFromClassResource(String path,
                                                          @NotNull Class<?> resourceClass,
                                                          double scale,
                                                          @NotNull Dimension2DDouble originalUserSize,
                                                          boolean useImageIO) throws IOException {
    InputStream stream = resourceClass.getResourceAsStream(path);
    if (stream == null) {
      return null;
    }

    try (stream) {
      return loadPng(stream, scale, originalUserSize, useImageIO);
    }
  }

  @ApiStatus.Internal
  public static @NotNull Image loadFromStream(@NotNull InputStream stream,
                                              @Nullable String path,
                                              float scale,
                                              @NotNull ImageLoader.Dimension2DDouble originalUserSize,
                                              @MagicConstant(flags = {USE_DARK, USE_SVG, USE_IMAGE_IO}) int flags) throws IOException {
    try {
      if (BitUtil.isSet(flags, USE_SVG)) {
        return SVGLoader.load(path, stream, scale, BitUtil.isSet(flags, USE_DARK), originalUserSize);
      }
      else {
        return loadPng(stream, scale, originalUserSize, BitUtil.isSet(flags, USE_IMAGE_IO));
      }
    }
    finally {
      stream.close();
    }
  }

  private static @NotNull Image loadPng(@NotNull InputStream stream, double scale, @NotNull Dimension2DDouble originalUserSize, boolean useImageIO) throws IOException {
    long start = StartUpMeasurer.getCurrentTimeIfEnabled();
    Image image;
    if (useImageIO) {
      image = ImageIO.read(stream);
    }
    else {
      if (stream instanceof BufferExposingByteArrayInputStream) {
        BufferExposingByteArrayInputStream byteInput = (BufferExposingByteArrayInputStream)stream;
        image = Toolkit.getDefaultToolkit().createImage(byteInput.getInternalBuffer(), 0, byteInput.available());
      }
      else {
        image = Toolkit.getDefaultToolkit().createImage(stream.readAllBytes());
      }
      waitForImage(image);
    }
    originalUserSize.setSize(image.getWidth(null) / scale, image.getHeight(null) / scale);
    if (start != -1) {
      IconLoadMeasurer.pngDecoding.addDurationStartedAt(start);
    }
    return image;
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

  // originalUserSize - The original user space size of the image. In case of SVG it's the size specified in the SVG doc.
  // Otherwise it's the size of the original image divided by the image's scale (defined by the extension @2x).
  private static @Nullable Image convertImage(@NotNull Image image,
                                              @Nullable List<ImageFilter> filters,
                                              @MagicConstant(flagsFromClass = ImageLoader.class) int flags,
                                              ScaleContext scaleContext,
                                              boolean isUpScaleNeeded,
                                              boolean isHiDpiNeeded,
                                              double imageScale,
                                              boolean isSvg,
                                              @NotNull ImageLoader.Dimension2DDouble originalUserSize) {
    if (isUpScaleNeeded && !isSvg) {
      double scale = adjustScaleFactor(BitUtil.isSet(flags, ALLOW_FLOAT_SCALING), (float)scaleContext.getScale(DerivedScaleType.PIX_SCALE));
      if (imageScale > 1) {
        // compensate the image original scale
        scale /= imageScale;
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
      image = new JBHiDPIScaledImage(image, originalUserSize.getWidth() * userScale, originalUserSize.getHeight() * userScale, BufferedImage.TYPE_INT_ARGB);
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
    float pixScale = (float)scaleContext.getScale(DerivedScaleType.PIX_SCALE);

    int i = path.lastIndexOf('.');
    final String name = i < 0 ? path : path.substring(0, i);
    String ext = i < 0 || (i == path.length() - 1) ? "" : path.substring(i + 1);
    float scale = adjustScaleFactor(BitUtil.isSet(flags, ALLOW_FLOAT_SCALING), pixScale);

    List<ImageDescriptor> list;
    if (!path.startsWith("file:") && path.contains("://")) {
      int qI = path.lastIndexOf('?');
      boolean isSvg = StringUtilRt.endsWithIgnoreCase(qI == -1 ? path : path.substring(0, qI), ".svg");
      list = Collections.singletonList(new ImageDescriptor(name + "." + ext, 1f, isSvg, true));
    }
    else {
      boolean isSvg = "svg".equalsIgnoreCase(ext);
      boolean isDark = BitUtil.isSet(flags, USE_DARK);
      boolean retina = JBUIScale.isHiDPI(pixScale);

      list = new ArrayList<>();
      if (!isSvg && BitUtil.isSet(flags, USE_SVG)) {
        addFileNameVariant(retina, isDark, true, name, ext, scale, list);
      }
      addFileNameVariant(retina, isDark, isSvg, name, ext, scale, list);
      if (isDark) {
        // fallback to non-dark
        addFileNameVariant(retina, false, isSvg, name, ext, scale, list);
        if (!isSvg && BitUtil.isSet(flags, USE_SVG)) {
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
    int flags = USE_SVG;
    flags = BitUtil.set(flags, ALLOW_FLOAT_SCALING, allowFloatScaling);
    flags = BitUtil.set(flags, USE_CACHE, useCache);
    flags = BitUtil.set(flags, USE_CACHE, StartupUiUtil.isUnderDarcula());
    //noinspection MagicConstant
    return loadFromUrl(url.toString(), null, flags, filters, ctx);
  }

  /**
   * Loads an image of available resolution (1x, 2x, ...) and scales to address the provided scale context.
   * Then wraps the image with {@link JBHiDPIScaledImage} if necessary.
   */
  public static @Nullable Image loadFromUrl(@NotNull String path,
                                            @Nullable Class<?> aClass,
                                            @MagicConstant(flagsFromClass = ImageLoader.class) int flags,
                                            @Nullable List<ImageFilter> filters,
                                            @NotNull ScaleContext scaleContext) {
    // We can't check all 3rd party plugins and convince the authors to add @2x icons.
    // In IDE-managed HiDPI mode with scale > 1.0 we scale images manually - pass isUpScaleNeeded = true
    return load(path, filters, aClass, flags, scaleContext, !path.endsWith(".svg"));
  }

  private static float adjustScaleFactor(boolean allowFloatScaling, float scale) {
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
    int flags = USE_SVG | ALLOW_FLOAT_SCALING | USE_CACHE;
    flags = BitUtil.set(flags, USE_DARK, StartupUiUtil.isUnderDarcula());
    //noinspection MagicConstant
    return load(path, null, aClass, flags, scaleContext, false);
  }

  public static Image loadFromBytes(byte @NotNull [] bytes) {
    return loadFromStream(new ByteArrayInputStream(bytes));
  }

  public static Image loadFromStream(@NotNull InputStream inputStream) {
    // for backward compatibility assume the image is hidpi-aware (includes default SYS_SCALE)
    ScaleContext scaleContext = ScaleContext.create();
    try (inputStream) {
      ImageLoader.Dimension2DDouble originalUserSize = new ImageLoader.Dimension2DDouble(0, 0);
      double scale = scaleContext.getScale(DerivedScaleType.PIX_SCALE);
      Image image = loadPng(inputStream, scale, originalUserSize, false);
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
    float scale = (float)scaleContext.getScale(DerivedScaleType.PIX_SCALE);
    ImageDescriptor imageDescriptor = new ImageDescriptor(file.toURI().toURL().toString(), scale, StringUtilRt.endsWithIgnoreCase(file.getPath(), ".svg"), file.getPath().contains("_dark."));
    Image icon = ImageUtil.ensureHiDPI(loadByDescriptor(imageDescriptor, USE_CACHE, null, new Dimension2DDouble(0, 0), null, null), scaleContext);
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