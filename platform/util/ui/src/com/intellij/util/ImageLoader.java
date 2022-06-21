// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.ui.icons.IconLoadMeasurer;
import com.intellij.ui.icons.ImageDescriptor;
import com.intellij.ui.scale.DerivedScaleType;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.io.URLUtil;
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
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
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
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
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

  private static @NotNull Logger getLogger() {
    return Logger.getInstance(ImageLoader.class);
  }

  @ApiStatus.Internal
  public static final class ImageCache {
    public static final ImageCache INSTANCE = new ImageCache();

    private ImageCache() {
    }

    private static final long CACHED_IMAGE_MAX_SIZE = (long)(SystemProperties.getFloatProperty("ide.cached.image.max.size", 1.5f) * 1024 * 1024);
    @SuppressWarnings("SSBasedInspection")
    private final Set<String> ioMissCache = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final ConcurrentMap<CacheKey, Pair<Image, Dimension2DDouble>> imageCache = CollectionFactory.createConcurrentSoftValueMap();
    // https://github.com/JetBrains/intellij-community/pull/1242
    private final ConcurrentMap<CacheKey, Image> largeImageCache = CollectionFactory.createConcurrentWeakValueMap();
    private final ConcurrentMap<Image, ImageLoader.Dimension2DDouble> largeImageDimensionMap = CollectionFactory.createConcurrentWeakMap();

    @ApiStatus.Internal
    public static boolean isIconTooLargeForCache(@NotNull Icon icon) {
      return 4L * icon.getIconWidth() * icon.getIconHeight() > CACHED_IMAGE_MAX_SIZE;
    }

    public void clearCache() {
      imageCache.clear();
      largeImageCache.clear();
      largeImageDimensionMap.clear();
      ioMissCache.clear();
    }
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
                                         @NotNull List<? super ImageDescriptor> list) {
    String _ext = isSvg ? "svg" : ext;
    float _scale = isSvg ? scale : retina ? 2 : 1;

    if (retina && isDark) {
      list.add(new ImageDescriptor(name + "@2x_dark." + _ext, _scale, isSvg, true));
    }
    list.add(new ImageDescriptor(name + (isDark ? "_dark" : "") + (retina ? "@2x" : "") + "." + _ext, _scale, isSvg, isDark));
    if (retina) {
      // a fallback to 1x icon
      list.add(new ImageDescriptor(name + (isDark ? "_dark" : "") + "." + _ext, isSvg ? scale : 1, isSvg, isDark));
    }
  }

  @ApiStatus.Internal
  public static @Nullable Image loadImage(@NotNull String path,
                                          @NotNull List<? extends ImageFilter> filters,
                                          @Nullable Class<?> resourceClass,
                                          @Nullable ClassLoader classLoader,
                                          @MagicConstant(flagsFromClass = ImageLoader.class) int flags,
                                          @NotNull ScaleContext scaleContext,
                                          boolean isUpScaleNeeded) {
    long start = StartUpMeasurer.getCurrentTimeIfEnabled();

    List<ImageDescriptor> descriptors = createImageDescriptorList(path, flags, scaleContext);
    ImageCache imageCache = ImageCache.INSTANCE;
    boolean ioExceptionThrown = false;
    boolean isHiDpiNeeded = StartupUiUtil.isJreHiDPI(scaleContext);
    ImageLoader.Dimension2DDouble originalUserSize = new Dimension2DDouble(0, 0);
    for (int i = 0; i < descriptors.size(); i++) {
      ImageDescriptor descriptor = descriptors.get(i);
      try {
        // check only for the first one, as io miss cache doesn't have scale
        Image image = loadByDescriptor(descriptor, flags, resourceClass, classLoader, originalUserSize, i == 0 ? imageCache.ioMissCache : null, imageCache, path);
        if (image == null) {
          continue;
        }
        if (start != -1) {
          IconLoadMeasurer.addLoading(descriptor.isSvg, start);
        }
        return convertImage(image, filters, flags, scaleContext, isUpScaleNeeded, isHiDpiNeeded, descriptor.scale, descriptor.isSvg);
      }
      catch (IOException e) {
        ioExceptionThrown = true;
      }
    }

    if (ioExceptionThrown) {
      imageCache.ioMissCache.add(path);
    }
    return null;
  }

  @ApiStatus.Internal
  public static @Nullable Image loadImageForStartUp(@NotNull String path,
                                                    @NotNull ClassLoader classLoader,
                                                    @MagicConstant(flagsFromClass = ImageLoader.class) int flags,
                                                    @NotNull ScaleContext scaleContext,
                                                    boolean isUpScaleNeeded) {
    List<ImageDescriptor> descriptors = createImageDescriptorList(path, flags, scaleContext);
    boolean isHiDpiNeeded = StartupUiUtil.isJreHiDPI(scaleContext);
    ImageLoader.Dimension2DDouble originalUserSize = new Dimension2DDouble(0, 0);
    for (ImageDescriptor descriptor : descriptors) {
      try {
        Image image = loadByDescriptorWithoutCache(descriptor, null, classLoader, originalUserSize);
        if (image == null) {
          continue;
        }
        return convertImage(image, Collections.emptyList(), flags, scaleContext, isUpScaleNeeded, isHiDpiNeeded, descriptor.scale, descriptor.isSvg
        );
      }
      catch (IOException ignore) {
      }
    }
    return null;
  }

  private static @Nullable Image loadByDescriptor(@NotNull ImageDescriptor descriptor,
                                                  @MagicConstant(flags = USE_CACHE) int flags,
                                                  @Nullable Class<?> resourceClass,
                                                  @Nullable ClassLoader classLoader,
                                                  @NotNull ImageLoader.Dimension2DDouble originalUserSize,
                                                  @Nullable Set<String> ioMissCache,
                                                  @NotNull ImageCache imageCache,
                                                  @Nullable String ioMissCacheKey) throws IOException {
    CacheKey cacheKey = null;
    if ((flags & USE_CACHE) == USE_CACHE && !SVGLoader.isColorRedefinitionContext()) {
      cacheKey = new CacheKey(descriptor.path, descriptor.isSvg ? descriptor.scale : 0);
      Pair<Image, Dimension2DDouble> pair = imageCache.imageCache.get(cacheKey);
      if (pair != null) {
        originalUserSize.setSize(pair.second);
        return pair.first;
      }

      Image image = imageCache.largeImageCache.get(cacheKey);
      if (image != null) {
        ImageLoader.Dimension2DDouble dimension = imageCache.largeImageDimensionMap.get(image);
        if (dimension != null) {
          originalUserSize.setSize(dimension);
          return image;
        }
      }
    }

    if (ioMissCache != null && ioMissCache.contains(ioMissCacheKey)) {
      return null;
    }

    Image image = loadByDescriptorWithoutCache(descriptor, resourceClass, classLoader, originalUserSize);
    if (image == null) {
      return null;
    }

    if (cacheKey != null) {
      if (4L * image.getWidth(null) * image.getHeight(null) <= ImageCache.CACHED_IMAGE_MAX_SIZE) {
        imageCache.imageCache.put(cacheKey, new Pair<>(image, originalUserSize));
      }
      else {
        imageCache.largeImageCache.put(cacheKey, image);
        imageCache.largeImageDimensionMap.put(image, originalUserSize);
      }
    }
    return image;
  }

  private static @Nullable Image loadByDescriptorWithoutCache(@NotNull ImageDescriptor descriptor,
                                                              @Nullable Class<?> resourceClass,
                                                              @Nullable ClassLoader classLoader,
                                                              @NotNull Dimension2DDouble originalUserSize) throws IOException {
    Image image;
    long start = StartUpMeasurer.getCurrentTimeIfEnabled();
    if (resourceClass == null && (classLoader == null || URLUtil.containsScheme(descriptor.path)) && !descriptor.path.startsWith("file://")) {
      URLConnection connection = new URL(descriptor.path).openConnection();
      if (connection instanceof HttpURLConnection) {
        connection.addRequestProperty("User-Agent", "IntelliJ");
      }

      try (InputStream stream = connection.getInputStream()) {
        if (descriptor.isSvg) {
          image = SVGLoader.load(descriptor.path, stream, descriptor.scale, descriptor.isDark, originalUserSize);
        }
        else {
          image = loadPng(stream, descriptor.scale, originalUserSize);
        }
      }
      if (start != -1) {
        IconLoadMeasurer.loadFromUrl.end(start);
      }
    }
    else {
      if (descriptor.isSvg) {
        image = SVGLoader.loadFromClassResource(resourceClass, classLoader, descriptor.path, 0, descriptor.scale, descriptor.isDark,
                                                originalUserSize);
      }
      else {
        image = loadPngFromClassResource(descriptor.path, resourceClass, classLoader, descriptor.scale, originalUserSize);
      }
      if (start != -1) {
        IconLoadMeasurer.loadFromResources.end(start);
      }
    }
    return image;
  }

  static byte @Nullable [] getResourceData(@NotNull String path, @Nullable Class<?> resourceClass, @Nullable ClassLoader classLoader)
    throws IOException {
    assert resourceClass != null || classLoader != null || path.startsWith("file://");

    if (classLoader != null) {
      boolean isAbsolute = path.startsWith("/");
      byte[] data = ResourceUtil.getResourceAsBytes(isAbsolute ? path.substring(1) : path, classLoader, true);
      if (data != null || isAbsolute) {
        return data;
      }
    }

    if (resourceClass != null) {
      try (InputStream stream = resourceClass.getResourceAsStream(path)) {
        return stream == null ? null : stream.readAllBytes();
      }
    }

    if (path.startsWith("file:/")) {
      Path nioPath = Path.of(URI.create(path));
      try {
        return Files.readAllBytes(nioPath);
      }
      catch (NoSuchFileException e) {
        return null;
      }
      catch (IOException e) {
        getLogger().warn(e);
      }
    }
    return null;
  }

  @ApiStatus.Internal
  public static @Nullable Image loadPngFromClassResource(String path,
                                                         @Nullable Class<?> resourceClass,
                                                         @Nullable ClassLoader classLoader,
                                                         double scale,
                                                         @NotNull Dimension2DDouble originalUserSize) throws IOException {
    byte[] data = getResourceData(path, resourceClass, classLoader);
    if (data == null) {
      return null;
    }
    return loadPng(new ByteArrayInputStream(data), scale, originalUserSize);
  }

  @ApiStatus.Internal
  public static @NotNull Image loadFromStream(@NotNull InputStream stream,
                                              @Nullable String path,
                                              float scale,
                                              @NotNull ImageLoader.Dimension2DDouble originalUserSize,
                                              @MagicConstant(flags = {USE_DARK, USE_SVG}) int flags) throws IOException {
    try (stream) {
      if ((flags & USE_SVG) == USE_SVG) {
        return SVGLoader.load(path, stream, scale, (flags & USE_DARK) == USE_DARK, originalUserSize);
      }
      else {
        return loadPng(stream, scale, originalUserSize);
      }
    }
  }

  private static @NotNull BufferedImage loadPng(@NotNull InputStream stream, double scale, @NotNull Dimension2DDouble originalUserSize) throws IOException {
    long start = StartUpMeasurer.getCurrentTimeIfEnabled();
    BufferedImage image;
    ImageReader reader = ImageIO.getImageReadersByFormatName("png").next();
    try (MemoryCacheImageInputStream imageInputStream = new MemoryCacheImageInputStream(stream)) {
      reader.setInput(imageInputStream, true, true);
      image = reader.read(0, null);
    }
    finally {
      reader.dispose();
    }
    originalUserSize.setSize(image.getWidth() / scale, image.getHeight() / scale);
    if (start != -1) {
      IconLoadMeasurer.pngDecoding.end(start);
    }
    return image;
  }

  // originalUserSize - The original user space size of the image. In case of SVG it's the size specified in the SVG doc.
  // Otherwise, it's the size of the original image divided by the image's scale (defined by the extension @2x).
  public static @Nullable Image convertImage(@NotNull Image image,
                                             @NotNull List<? extends ImageFilter> filters,
                                             @MagicConstant(flagsFromClass = ImageLoader.class) int flags,
                                             ScaleContext scaleContext,
                                             boolean isUpScaleNeeded,
                                             boolean isHiDpiNeeded,
                                             double imageScale,
                                             boolean isSvg) {
    if (isUpScaleNeeded && !isSvg) {
      double scale = adjustScaleFactor((flags & ALLOW_FLOAT_SCALING) == ALLOW_FLOAT_SCALING, (float)scaleContext.getScale(DerivedScaleType.PIX_SCALE));
      if (imageScale > 1) {
        // compensate the image original scale
        scale /= imageScale;
      }
      image = scaleImage(image, scale);
    }

    if (!filters.isEmpty()) {
      Toolkit toolkit = Toolkit.getDefaultToolkit();
      for (ImageFilter filter : filters) {
        if (filter != null) {
          image = toolkit.createImage(new FilteredImageSource(ImageUtil.toBufferedImage(image, false).getSource(), filter));
        }
      }
    }

    if (isHiDpiNeeded) {
      // The {originalUserSize} can contain calculation inaccuracy. If we use it to derive the HiDPI image scale
      // in JBHiDPIScaledImage, the derived scale will also be inaccurate and this will cause distortions
      // when the image is painted on a scaled (hidpi) screen graphics, see
      // StartupUiUtil.drawImage(Graphics, Image, Rectangle, Rectangle, BufferedImageOp, ImageObserver).
      //
      // To avoid that, we instead directly use the provided ScaleContext which contains correct ScaleContext.SYS_SCALE,
      // the image user space size will then be derived by JBHiDPIScaledImage (it is assumed the derived size is equal to
      // {originalUserSize} * DerivedScaleType.EFF_USR_SCALE, taking into account calculation accuracy).
      image = new JBHiDPIScaledImage(image, scaleContext, BufferedImage.TYPE_INT_ARGB);
    }
    return image;
  }

  public static @NotNull List<ImageDescriptor> getImageDescriptors(@NotNull String path,
                                                                   @MagicConstant(flagsFromClass = ImageLoader.class) int flags,
                                                                   @NotNull ScaleContext scaleContext) {
    return createImageDescriptorList(path, flags, scaleContext);
  }

  private static @NotNull List<ImageDescriptor> createImageDescriptorList(@NotNull String path,
                                                                          @MagicConstant(flagsFromClass = ImageLoader.class) int flags,
                                                                          @NotNull ScaleContext scaleContext) {
    // Prefer retina images for HiDPI scale, because downscaling
    // retina images provides a better result than up-scaling non-retina images.
    float pixScale = (float)scaleContext.getScale(DerivedScaleType.PIX_SCALE);

    int i = path.lastIndexOf('.');
    final String name = i < 0 ? path : path.substring(0, i);
    String ext = i < 0 || (i == path.length() - 1) ? "" : path.substring(i + 1);
    float scale = adjustScaleFactor((flags & ALLOW_FLOAT_SCALING) == ALLOW_FLOAT_SCALING, pixScale);

    List<ImageDescriptor> list;
    if (!path.startsWith("file:") && path.contains("://")) {
      int qI = path.lastIndexOf('?');
      boolean isSvg = StringUtilRt.endsWithIgnoreCase(qI == -1 ? path : path.substring(0, qI), ".svg");
      list = Collections.singletonList(new ImageDescriptor(name + "." + ext, 1f, isSvg, true));
    }
    else {
      boolean isSvg = "svg".equalsIgnoreCase(ext);
      boolean isDark = (flags & USE_DARK) == USE_DARK;
      boolean retina = JBUIScale.isHiDPI(pixScale);

      list = new ArrayList<>();
      if (!isSvg && (flags & USE_SVG) == USE_SVG) {
        addFileNameVariant(retina, isDark, true, name, ext, scale, list);
      }
      addFileNameVariant(retina, isDark, isSvg, name, ext, scale, list);
      if (isDark) {
        // fallback to non-dark
        addFileNameVariant(retina, false, isSvg, name, ext, scale, list);
        if (!isSvg && (flags & USE_SVG) == USE_SVG) {
          addFileNameVariant(false, false, true, name, ext, scale, list);
        }
      }
    }
    return list;
  }

  public static final Component ourComponent = new Component() {
  };

  public static @Nullable Image loadFromUrl(@NotNull URL url) {
    int flags = USE_SVG | USE_CACHE | ALLOW_FLOAT_SCALING;
    if (StartupUiUtil.isUnderDarcula()) {
      flags |= USE_DARK;
    }
    String path = url.toString();
    return loadImage(path, Collections.emptyList(), null, null, flags, ScaleContext.create(), !path.endsWith(".svg"));
  }

  /**
   * Loads an image of available resolution (1x, 2x, ...) and scales to address the provided scale context.
   * Then wraps the image with {@link JBHiDPIScaledImage} if necessary.
   */
  public static @Nullable Image loadFromUrl(@NotNull String path,
                                            @Nullable Class<?> aClass,
                                            @MagicConstant(flagsFromClass = ImageLoader.class) int flags,
                                            @NotNull ScaleContext scaleContext) {
    // We can't check all 3rd party plugins and convince the authors to add @2x icons.
    // In IDE-managed HiDPI mode with scale > 1.0 we scale images manually - pass isUpScaleNeeded = true
    return loadImage(path, Collections.emptyList(), aClass, null, flags, scaleContext, !path.endsWith(".svg"));
  }

  public static float adjustScaleFactor(boolean allowFloatScaling, float scale) {
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
    if (StartupUiUtil.isUnderDarcula()) {
      flags |= USE_DARK;
    }
    return loadImage(path, Collections.emptyList(), aClass, null, flags, scaleContext, false);
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
      Image image = loadPng(inputStream, scale, originalUserSize);
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
    Image icon = ImageUtil.ensureHiDPI(loadByDescriptor(imageDescriptor, USE_CACHE, null, null, new Dimension2DDouble(0, 0), null, ImageCache.INSTANCE, null), scaleContext);
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