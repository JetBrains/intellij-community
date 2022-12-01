// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.ui.icons.IconLoadMeasurer;
import com.intellij.ui.icons.ImageDescriptor;
import com.intellij.ui.icons.LoadIconParameters;
import com.intellij.ui.scale.DerivedScaleType;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.ui.svg.SvgCacheMapper;
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
import java.util.List;
import java.util.*;
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

    private final ConcurrentMap<CacheKey, Image> imageCache = CollectionFactory.createConcurrentSoftValueMap();
    // https://github.com/JetBrains/intellij-community/pull/1242
    private final ConcurrentMap<CacheKey, Image> largeImageCache = CollectionFactory.createConcurrentWeakValueMap();

    @ApiStatus.Internal
    public static boolean isIconTooLargeForCache(@NotNull Icon icon) {
      return 4L * icon.getIconWidth() * icon.getIconHeight() > CACHED_IMAGE_MAX_SIZE;
    }

    public void clearCache() {
      imageCache.clear();
      largeImageCache.clear();
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
                                         boolean isStroke,
                                         boolean isSvg,
                                         String name,
                                         String ext,
                                         float scale,
                                         @NotNull List<? super ImageDescriptor> list) {
    String _ext = isSvg ? "svg" : ext;
    float _scale = isSvg ? scale : retina ? 2 : 1;

    if (isStroke) {
      list.add(new ImageDescriptor(name + "_stroke." + _ext, _scale, isSvg, false, true));
    }
    if (retina && isDark) {
      list.add(new ImageDescriptor(name + "@2x_dark." + _ext, _scale, isSvg, true, false));
    }
    list.add(new ImageDescriptor(name + (isDark ? "_dark" : "") + (retina ? "@2x" : "") + "." + _ext, _scale, isSvg, isDark, false));
    if (retina) {
      // a fallback to 1x icon
      list.add(new ImageDescriptor(name + (isDark ? "_dark" : "") + "." + _ext, isSvg ? scale : 1, isSvg, isDark, false));
    }
  }

  // Some duplication here: isDark presents in parameters and in flags
  @ApiStatus.Internal
  public static @Nullable Image loadImage(@NotNull String path,
                                          @NotNull LoadIconParameters parameters,
                                          @Nullable Class<?> resourceClass,
                                          @Nullable ClassLoader classLoader,
                                          @MagicConstant(flagsFromClass = ImageLoader.class) int flags,
                                          boolean isUpScaleNeeded) {
    long start = StartUpMeasurer.getCurrentTimeIfEnabled();

    List<ImageDescriptor> descriptors = createImageDescriptorList(path, flags, parameters.scaleContext);
    ImageCache imageCache = ImageCache.INSTANCE;
    boolean ioExceptionThrown = false;
    for (int i = 0; i < descriptors.size(); i++) {
      ImageDescriptor descriptor = descriptors.get(i);
      try {
        // check only for the first one, as io miss cache doesn't have scale
        Image image = loadByDescriptor(descriptor, flags, resourceClass, classLoader,
                                       i == 0 ? imageCache.ioMissCache : null, imageCache, path, parameters.colorPatcher);
        if (image == null) {
          continue;
        }
        if (start != -1) {
          IconLoadMeasurer.addLoading(descriptor.isSvg, start);
        }
        boolean isHiDpiNeeded = StartupUiUtil.isJreHiDPI(parameters.scaleContext);
        return convertImage(image, parameters.filters, flags, parameters.scaleContext, isUpScaleNeeded,
                            isHiDpiNeeded, descriptor.scale, descriptor.isSvg);
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
  public static @Nullable BufferedImage loadImageForStartUp(@NotNull String requestedPath, @NotNull ClassLoader classLoader) {
    ScaleContext scaleContext = ScaleContext.create();
    List<ImageDescriptor> descriptors = createImageDescriptorList(requestedPath, ALLOW_FLOAT_SCALING, scaleContext);
    for (ImageDescriptor descriptor : descriptors) {
      try {
        byte[] data = getResourceData(descriptor.path, null, classLoader);
        if (data == null) {
          continue;
        }

        Image image;
        if (descriptor.isSvg) {
          return SVGLoader.loadWithoutCache(data, descriptor.scale);
        }
        else {
          image = loadPng(new ByteArrayInputStream(data), descriptor.scale, null);
          float scale = (float)scaleContext.getScale(DerivedScaleType.PIX_SCALE);
          if (descriptor.scale > 1) {
            // compensate the image original scale
            scale /= descriptor.scale;
          }
          return (BufferedImage)scaleImage(image, scale);
        }
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
                                                  @Nullable Set<String> ioMissCache,
                                                  @NotNull ImageCache imageCache,
                                                  @Nullable String ioMissCacheKey,
                                                  @Nullable SVGLoader.SvgElementColorPatcherProvider colorPatcher) throws IOException {
    CacheKey cacheKey = null;
    boolean tmpPatcher = false;
    byte[] digest = null;
    if (colorPatcher != null) {
      SVGLoader.SvgElementColorPatcher subPatcher = colorPatcher.forPath(descriptor.path);
      if (subPatcher != null) {
        digest = subPatcher.digest();
        if (digest == null) {
          tmpPatcher = true;
        }
      }
    }
    if (digest == null) {
      digest = SVGLoader.INSTANCE.getDEFAULT_THEME();
    }

    if ((flags & USE_CACHE) == USE_CACHE && !tmpPatcher) {
      cacheKey = new CacheKey(descriptor.path, descriptor.isSvg ? descriptor.scale : 0, digest);
      Image image = imageCache.imageCache.get(cacheKey);
      if (image != null) {
        return image;
      }

      image = imageCache.largeImageCache.get(cacheKey);
      if (image != null) {
        return image;
      }
    }

    if (ioMissCache != null && ioMissCache.contains(ioMissCacheKey)) {
      return null;
    }

    Image image = loadByDescriptorWithoutCache(descriptor, resourceClass, classLoader, colorPatcher);
    if (image == null) {
      return null;
    }

    if (cacheKey != null) {
      if (4L * image.getWidth(null) * image.getHeight(null) <= ImageCache.CACHED_IMAGE_MAX_SIZE) {
        imageCache.imageCache.put(cacheKey, image);
      }
      else {
        imageCache.largeImageCache.put(cacheKey, image);
      }
    }
    return image;
  }

  private static @Nullable Image loadByDescriptorWithoutCache(@NotNull ImageDescriptor descriptor,
                                                              @Nullable Class<?> resourceClass,
                                                              @Nullable ClassLoader classLoader,
                                                              @Nullable SVGLoader.SvgElementColorPatcherProvider colorPatcher
  ) throws IOException {
    Image image;
    long start = StartUpMeasurer.getCurrentTimeIfEnabled();
    if (resourceClass == null && (classLoader == null || URLUtil.containsScheme(descriptor.path)) && !descriptor.path.startsWith("file://")) {
      URLConnection connection = new URL(descriptor.path).openConnection();
      if (connection instanceof HttpURLConnection) {
        connection.addRequestProperty("User-Agent", "IntelliJ");
      }

      try (InputStream stream = connection.getInputStream()) {
        if (descriptor.isSvg) {
          image = SVGLoader.load(descriptor.path, stream, descriptor.getSvgMapper(), colorPatcher, null);
        }
        else {
          image = loadPng(stream, descriptor.scale, null);
        }
      }
      if (start != -1) {
        IconLoadMeasurer.loadFromUrl.end(start);
      }
    }
    else {
      if (descriptor.isSvg) {
        image = SVGLoader.INSTANCE.loadFromClassResource(resourceClass, classLoader, descriptor.path, 0, descriptor.getSvgMapper(),
                                                         colorPatcher, null);
      }
      else {
        image = loadPngFromClassResource(descriptor.path, resourceClass, classLoader, descriptor.scale, null);
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
                                                         float scale,
                                                         @Nullable Dimension2DDouble originalUserSize) throws IOException {
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
        SvgCacheMapper mapper = new SvgCacheMapper(scale, (flags & USE_DARK) == USE_DARK, false);
        return SVGLoader.load(path, stream, mapper, null, originalUserSize);
      }
      else {
        return loadPng(stream, scale, originalUserSize);
      }
    }
  }

  private static @NotNull BufferedImage loadPng(@NotNull InputStream stream, float scale, @Nullable Dimension2DDouble originalUserSize) throws IOException {
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
    if (originalUserSize != null) {
      originalUserSize.setSize(image.getWidth() / scale, image.getHeight() / scale);
    }
    if (start != -1) {
      IconLoadMeasurer.pngDecoding.end(start);
    }
    return image;
  }

  public static @Nullable Image convertImage(@NotNull Image image,
                                             @NotNull List<? extends ImageFilter> filters,
                                             @MagicConstant(flagsFromClass = ImageLoader.class) int flags,
                                             ScaleContext scaleContext,
                                             boolean isUpScaleNeeded,
                                             boolean isHiDpiNeeded,
                                             float imageScale,
                                             boolean isSvg) {
    if (isUpScaleNeeded && !isSvg) {
      float scale = adjustScaleFactor((flags & ALLOW_FLOAT_SCALING) == ALLOW_FLOAT_SCALING, (float)scaleContext.getScale(DerivedScaleType.PIX_SCALE));
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
    // prefer retina images for HiDPI scale, because downscaling retina images provides a better result than up-scaling non-retina images
    float pixScale = (float)scaleContext.getScale(DerivedScaleType.PIX_SCALE);

    int i = path.lastIndexOf('.');
    String name = i < 0 ? path : path.substring(0, i);
    String ext = i < 0 || (i == path.length() - 1) ? "" : path.substring(i + 1);
    float scale = adjustScaleFactor((flags & ALLOW_FLOAT_SCALING) == ALLOW_FLOAT_SCALING, pixScale);

    List<ImageDescriptor> list;
    if (!path.startsWith("file:") && path.contains("://")) {
      int qI = path.lastIndexOf('?');
      boolean isSvg = StringUtilRt.endsWithIgnoreCase(qI == -1 ? path : path.substring(0, qI), ".svg");
      list = Collections.singletonList(new ImageDescriptor(name + "." + ext, 1f, isSvg, true, false));
    }
    else {
      boolean isSvg = "svg".equalsIgnoreCase(ext);
      boolean isDark = (flags & USE_DARK) == USE_DARK;
      boolean retina = JBUIScale.isHiDPI(pixScale);

      list = new ArrayList<>();
      if (!isSvg && (flags & USE_SVG) == USE_SVG) {
        addFileNameVariant(retina, isDark, true, false, name, ext, scale, list);
      }
      addFileNameVariant(retina, isDark, false, isSvg, name, ext, scale, list);
      if (isDark) {
        // fallback to non-dark
        addFileNameVariant(retina, false, false, isSvg, name, ext, scale, list);
        if (!isSvg && (flags & USE_SVG) == USE_SVG) {
          addFileNameVariant(false, false, false, true, name, ext, scale, list);
        }
      }
    }
    return list;
  }

  public static final Component ourComponent = new Component() {
  };

  public static @Nullable Image loadFromUrl(@NotNull URL url) {
    int flags = USE_SVG | USE_CACHE | ALLOW_FLOAT_SCALING;
    boolean isDark = StartupUiUtil.isUnderDarcula();
    if (isDark) {
      flags |= USE_DARK;
    }
    String path = url.toString();
    return loadImage(path, LoadIconParameters.defaultParameters(isDark), null, null, flags, !path.endsWith(".svg"));
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
    LoadIconParameters parameters = new LoadIconParameters(Collections.emptyList(), scaleContext, (flags & USE_DARK) == USE_DARK, null, false);
    return loadImage(path, parameters, aClass, null, flags, !path.endsWith(".svg"));
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
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public static @Nullable Image loadFromResource(@NonNls @NotNull String s) {
    Class<?> callerClass = ReflectionUtil.getGrandCallerClass();
    return callerClass == null ? null : loadFromResource(s, callerClass);
  }

  public static @Nullable Image loadFromResource(@NonNls @NotNull String path, @NotNull Class<?> aClass) {
    int flags = USE_SVG | ALLOW_FLOAT_SCALING | USE_CACHE;
    boolean isDark = StartupUiUtil.isUnderDarcula();
    if (isDark) {
      flags |= USE_DARK;
    }
    return loadImage(path, LoadIconParameters.defaultParameters(isDark), aClass, null, flags, false);
  }

  public static Image loadFromBytes(byte @NotNull [] bytes) {
    return loadFromStream(new ByteArrayInputStream(bytes));
  }

  public static Image loadFromStream(@NotNull InputStream inputStream) {
    // for backward compatibility assume the image is hidpi-aware (includes default SYS_SCALE)
    ScaleContext scaleContext = ScaleContext.create();
    try (inputStream) {
      ImageLoader.Dimension2DDouble originalUserSize = new ImageLoader.Dimension2DDouble(0, 0);
      float scale = (float)scaleContext.getScale(DerivedScaleType.PIX_SCALE);
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

  @SuppressWarnings("unused")
  public static @Nullable Image loadCustomIcon(@NotNull File file) throws IOException {
    return loadCustomIcon(file.toURI().toURL());
  }

  public static @Nullable Image loadCustomIcon(@NotNull URL url) throws IOException {
    String iconPath = url.toString();
    ScaleContext scaleContext = ScaleContext.create();
    // probably, need implement naming conventions: filename ends with @2x => HiDPI (scale=2)
    float scale = (float)scaleContext.getScale(DerivedScaleType.PIX_SCALE);
    ImageDescriptor imageDescriptor = new ImageDescriptor(iconPath, scale, StringUtilRt.endsWithIgnoreCase(iconPath, ".svg"), iconPath.contains("_dark."), iconPath.contains("_stroke."));
    Image icon = ImageUtil.ensureHiDPI(
      loadByDescriptor(imageDescriptor, USE_CACHE, null, null, null, ImageCache.INSTANCE, null, null),
      scaleContext);
    if (icon == null) {
      return null;
    }

    int w = icon.getWidth(null);
    int h = icon.getHeight(null);
    if (w <= 0 || h <= 0) {
      getLogger().error("negative image size: w=" + w + ", h=" + h + ", path=" + iconPath);
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
  private final byte @NotNull [] digest;

  CacheKey(@NotNull String path, double scale, byte @NotNull [] digest) {
    this.path = path;
    this.scale = scale;
    this.digest = digest;
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
    return key.scale == scale && path.equals(key.path) && Arrays.equals(key.digest, digest);
  }

  @Override
  public int hashCode() {
    long temp = Double.doubleToLongBits(scale);
    int result = Objects.hash(path, (int)(temp ^ (temp >>> 32)));
    result = 31 * result + Arrays.hashCode(digest);
    return result;
  }
}