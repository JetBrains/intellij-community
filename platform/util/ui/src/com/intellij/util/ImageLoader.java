// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.ui.icons.IconLoadMeasurer;
import com.intellij.ui.icons.ImageDescriptor;
import com.intellij.ui.icons.ImageType;
import com.intellij.ui.scale.DerivedScaleType;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
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
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageFilter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ImageLoader {
  public static final int ALLOW_FLOAT_SCALING = 0x01;
  public static final byte USE_CACHE = 0x02;
  public static final int DARK = 0x04;
  public static final int FIND_SVG = 0x08;

  private static @NotNull Logger getLogger() {
    return Logger.getInstance(ImageLoader.class);
  }

  public static final long CACHED_IMAGE_MAX_SIZE = (long)(SystemProperties.getFloatProperty("ide.cached.image.max.size", 1.5f) * 1024 * 1024);

  @SuppressWarnings("UnusedDeclaration") // set from com.intellij.internal.IconsLoadTime
  private static LoadFunction measureLoad;

  @ApiStatus.Internal
  public interface LoadFunction {
    Image load(@Nullable LoadFunction delegate, @NotNull ImageType type) throws IOException;
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
                                         boolean dark,
                                         ImageType type,
                                         String name,
                                         String ext,
                                         double scale,
                                         @NotNull List<ImageDescriptor> list) {
    boolean isSvg = type == ImageType.SVG;
    String _ext = isSvg ? "svg" : ext;
    double _scale = isSvg ? scale : (retina ? 2 : 1);

    list.add(new ImageDescriptor(name + (dark ? "_dark" : "") + (retina ? "@2x" : "") + "." + _ext, _scale, type));
    if (retina && dark) {
      list.add(new ImageDescriptor(name + "@2x_dark" + "." + _ext, _scale, type));
    }
    if (retina) {
      // a fallback to 1x icon
      list.add(new ImageDescriptor(name + (dark ? "_dark" : "") + "." + _ext, ImageType.SVG == type ? scale : 1, type));
    }
  }

  public static void clearCache() {
    ImageDescriptorList.IO_MISS_CACHE.clear();
  }

  public static final class ImageDescriptorList {
    private static final Set<String> IO_MISS_CACHE = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final List<ImageDescriptor> list;
    private final String path;
    private final ImageType type;

    private ImageDescriptorList(@NotNull List<ImageDescriptor> list, @NotNull String path, @NotNull ImageType type) {
      this.list = list;
      this.path = path;
      this.type = type;
    }

    public @NotNull List<@NotNull ImageDescriptor> getDescriptors() {
      return list;
    }

    public @Nullable Image load(@NotNull ImageConverterChain converters) {
      return load(converters, true, null);
    }

    public @Nullable Image load(@NotNull ImageConverterChain converters, boolean useCache, @Nullable Class<?> resourceClass) {
      String cacheKey = path + "." + type.name();
      if (IO_MISS_CACHE.contains(cacheKey)) {
        return null;
      }

      long start = StartUpMeasurer.getCurrentTimeIfEnabled();

      boolean ioExceptionThrown = false;
      Image result = null;
      for (ImageDescriptor descriptor : list) {
        try {
          Image image = descriptor.load(useCache, resourceClass);
          if (image == null) {
            continue;
          }

          result = converters.convert(image, descriptor);
          if (start != -1) {
            IconLoadMeasurer.addLoading(descriptor.type, (int)(StartUpMeasurer.getCurrentTime() - start));
          }
          return result;
        }
        catch (IOException e) {
          ioExceptionThrown = true;
        }
      }

      if (ioExceptionThrown) {
        IO_MISS_CACHE.add(cacheKey);
      }
      return result;
    }

    public static @NotNull ImageDescriptorList create(@NotNull String path,
                                                      @MagicConstant(flags = {ALLOW_FLOAT_SCALING, DARK, FIND_SVG}) int flags,
                                                      @NotNull ScaleContext scaleContext) {
      // Prefer retina images for HiDPI scale, because downscaling
      // retina images provides a better result than up-scaling non-retina images.
      double pixScale = scaleContext.getScale(DerivedScaleType.PIX_SCALE);
      boolean retina = JBUIScale.isHiDPI(pixScale);

      ImageType imageType = BitUtil.isSet(flags, FIND_SVG) ? ImageType.SVG : ImageType.IMG;

      int i = path.lastIndexOf('.');
      String name = i < 0 ? path : path.substring(0, i);
      String ext = i < 0 || (i == path.length() - 1) ? "" : path.substring(i + 1);
      double scale = adjustScaleFactor(BitUtil.isSet(flags, ALLOW_FLOAT_SCALING), pixScale);

      List<ImageDescriptor> list;
      if (!path.startsWith("file:") && path.contains("://")) {
        ImageType type1 =
          StringUtilRt.endsWithIgnoreCase(StringUtil.substringBeforeLast(path, "?"), ".svg") ? ImageType.SVG : ImageType.IMG;
        list = Collections.singletonList(new ImageDescriptor(name + "." + ext, 1.0, type1, true));
      }
      else {
        boolean isDark = BitUtil.isSet(flags, DARK);
        list = retina || isDark ? new ArrayList<>() : new SmartList<>();
        addFileNameVariant(retina, isDark, imageType, name, ext, scale, list);
        if (isDark) {
          // fallback to non-dark
          addFileNameVariant(retina, false, imageType, name, ext, scale, list);
        }
      }
      return new ImageDescriptorList(list, name, imageType);
    }
  }

  private interface ImageConverter {
    Image convert(@Nullable Image source, ImageDescriptor desc);
  }

  private static final class ImageConverterChain {
    private final List<ImageConverter> chain = new ArrayList<>();

    ImageConverterChain(@Nullable List<ImageFilter> filters) {
      if (filters == null || filters.isEmpty()) {
        return;
      }

      for (ImageFilter filter : filters) {
        if (filter != null) {
          chain.add(new ImageConverter() {
            @Override
            public Image convert(Image source, ImageDescriptor desc) {
              return ImageUtil.filter(source, filter);
            }
          });
        }
      }
    }

    ImageConverterChain withHiDPI(@Nullable ScaleContext scaleContext) {
      if (scaleContext == null) {
        return this;
      }
      chain.add(new ImageConverter() {
        @Override
        public Image convert(Image source, ImageDescriptor desc) {
          double usrScale = scaleContext.getScale(DerivedScaleType.EFF_USR_SCALE);
          return ImageUtil.ensureHiDPI(source, scaleContext,
                                       desc.origUsrSize.getWidth() * usrScale,
                                       desc.origUsrSize.getHeight() * usrScale);
        }
      });
      return this;
    }

    public Image convert(Image image, ImageDescriptor desc) {
      for (ImageConverter f : chain) {
        image = f.convert(image, desc);
      }
      return image;
    }
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
    return loadFromUrl(url, null, flags, filters, ctx);
  }

  /**
   * Loads an image of available resolution (1x, 2x, ...) and scales to address the provided scale context.
   * Then wraps the image with {@link JBHiDPIScaledImage} if necessary.
   */
  public static @Nullable Image loadFromUrl(@NotNull URL url,
                                            @Nullable Class<?> aClass,
                                            @MagicConstant(flags = {ALLOW_FLOAT_SCALING, USE_CACHE, DARK, FIND_SVG}) int flags,
                                            @Nullable List<ImageFilter> filters,
                                            @NotNull ScaleContext scaleContext) {
    return loadFromUrl(url.toString(), aClass, flags, filters, scaleContext);
  }

  /**
   * Loads an image of available resolution (1x, 2x, ...) and scales to address the provided scale context.
   * Then wraps the image with {@link JBHiDPIScaledImage} if necessary.
   */
  public static @Nullable Image loadFromUrl(@NotNull String path,
                                            @Nullable Class<?> aClass,
                                            @MagicConstant(flags = {ALLOW_FLOAT_SCALING, USE_CACHE, DARK, FIND_SVG}) int flags,
                                            @Nullable List<ImageFilter> filters,
                                            @NotNull ScaleContext scaleContext) {
    // We can't check all 3rd party plugins and convince the authors to add @2x icons.
    // In IDE-managed HiDPI mode with scale > 1.0 we scale images manually.
    ImageConverterChain converters = new ImageConverterChain(filters);
    if (!path.endsWith(".svg")) {
      converters.chain.add(new ImageConverter() {
        @Override
        public Image convert(Image source, ImageDescriptor desc) {
          if (source == null || desc.type == ImageType.SVG) {
            return source;
          }

          double scale = adjustScaleFactor(BitUtil.isSet(flags, ALLOW_FLOAT_SCALING), scaleContext.getScale(DerivedScaleType.PIX_SCALE));
          if (desc.scale > 1) {
            // compensate the image original scale
            scale /= desc.scale;
          }
          return scaleImage(source, scale);
        }
      });
    }
    converters.withHiDPI(scaleContext);
    return ImageDescriptorList.create(path, flags, scaleContext)
      .load(converters, BitUtil.isSet(flags, USE_CACHE), aClass);
  }

  private static double adjustScaleFactor(boolean allowFloatScaling, double scale) {
    return allowFloatScaling ? scale : JBUIScale.isHiDPI(scale) ? 2f : 1f;
  }

  @NotNull
  public static Image scaleImage(@NotNull Image image, double scale) {
    if (scale == 1.0) return image;

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
    return Scalr.resize(ImageUtil.toBufferedImage(image), Scalr.Method.QUALITY, Scalr.Mode.FIT_EXACT, width, height, (BufferedImageOp[])null);
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

  @Nullable
  public static Image loadFromResource(@NonNls @NotNull String s) {
    Class<?> callerClass = ReflectionUtil.getGrandCallerClass();
    return callerClass == null ? null : loadFromResource(s, callerClass);
  }

  public static @Nullable Image loadFromResource(@NonNls @NotNull String path, @NotNull Class<?> aClass) {
    ScaleContext scaleContext = ScaleContext.create();
    int flags = FIND_SVG | ALLOW_FLOAT_SCALING;
    if (StartupUiUtil.isUnderDarcula()) {
      flags |= DARK;
    }
    return ImageDescriptorList.create(path, flags, scaleContext)
      .load(new ImageConverterChain(null).withHiDPI(scaleContext), true, aClass);
  }

  public static Image loadFromBytes(byte @NotNull [] bytes) {
    return loadFromStream(new ByteArrayInputStream(bytes));
  }

  public static Image loadFromStream(@NotNull InputStream inputStream) {
    // for backward compatibility assume the image is hidpi-aware (includes default SYS_SCALE)
    return loadFromStream(inputStream, ScaleContext.create());
  }

  /**
   * The scale context describes the image the stream presents.
   */
  private static @Nullable Image loadFromStream(@NotNull InputStream inputStream,
                                                @NotNull ScaleContext scaleContext) {
    try {
      ImageDescriptor imageDescriptor = new ImageDescriptor(scaleContext.getScale(DerivedScaleType.PIX_SCALE));
      Image image = imageDescriptor.loadFromStream(inputStream, null, null);
      return new ImageConverterChain(null)
        .withHiDPI(scaleContext)
        .convert(image, imageDescriptor);
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
    ImageDescriptor imageDescriptor = new ImageDescriptor(file.toURI().toURL().toString(), scale, StringUtilRt.endsWithIgnoreCase(file.getPath(), ".svg") ? ImageType.SVG : ImageType.IMG);
    Image icon = ImageUtil.ensureHiDPI(imageDescriptor.load(true, null), scaleContext);
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
