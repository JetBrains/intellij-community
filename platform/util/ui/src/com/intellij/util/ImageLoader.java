// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.ui.icons.IconLoadMeasurer;
import com.intellij.ui.icons.ImageDescriptor;
import com.intellij.ui.icons.ImageType;
import com.intellij.ui.scale.DerivedScaleType;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.ui.scale.ScaleType;
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
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public final class ImageLoader implements Serializable {
  public static final int ALLOW_FLOAT_SCALING = 0x01;
  public static final byte USE_CACHE = 0x02;
  public static final int DARK = 0x04;
  public static final int FIND_SVG = 0x08;

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance("#com.intellij.util.ImageLoader");
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

  private static final class ImageDescriptorListBuilder {
    private final List<ImageDescriptor> list = new SmartList<>();
    final String name;
    final String ext;
    final boolean svg;
    final double scale;

    ImageDescriptorListBuilder(String name, String ext, boolean svg, double scale) {
      this.name = name;
      this.ext = ext;
      this.svg = svg;
      this.scale = scale;
    }

    void add(boolean retina, boolean dark) {
      if (svg) {
        add(retina, dark, ImageType.SVG);
      }
      add(retina, dark, ImageType.IMG);
    }

    void add(boolean retina, boolean dark, ImageType type) {
      String _ext = ImageType.SVG == type ? "svg" : ext;
      double _scale = ImageType.SVG == type ? scale : retina ? 2 : 1;

      list.add(new ImageDescriptor(name + (dark ? "_dark" : "") + (retina ? "@2x" : "") + "." + _ext, _scale, type));
      if (retina && dark) {
        list.add(new ImageDescriptor(name + "@2x_dark" + "." + _ext, _scale, type));
      }
      if (retina) {
        // a fallback to 1x icon
        list.add(new ImageDescriptor(name + (dark ? "_dark" : "") + "." + _ext, ImageType.SVG == type ? scale : 1, type));
      }
    }

    void add(ImageType type) {
      list.add(new ImageDescriptor(name + "." + ext, 1.0, type, true));
    }

    @NotNull
    List<ImageDescriptor> build() {
      return list;
    }
  }

  private static final class ImageDescriptorList {
    private final List<ImageDescriptor> list;

    private ImageDescriptorList(@NotNull List<ImageDescriptor> list) {
      this.list = list;
    }

    @Nullable
    public Image load() {
      return load(ImageConverterChain.create());
    }

    @Nullable
    public Image load(@NotNull ImageConverterChain converters) {
      return load(converters, true, null);
    }

    @Nullable
    public Image load(@NotNull ImageConverterChain converters, boolean useCache, @Nullable Class<?> resourceClass) {
      long start = StartUpMeasurer.isEnabled() ? StartUpMeasurer.getCurrentTime() : -1;

      Image result = null;
      for (ImageDescriptor descriptor : list) {
        try {
          Image image = descriptor.load(useCache, resourceClass);
          if (image == null) {
            continue;
          }

          getLogger().debug("Loaded image: " + descriptor);
          result = converters.convert(image, descriptor);
          if (start != -1) {
            IconLoadMeasurer.addLoading(descriptor.type, (int)(StartUpMeasurer.getCurrentTime() - start));
          }
          break;
        }
        catch (IOException ignore) {
        }
      }
      return result;
    }

    @NotNull
    public static ImageDescriptorList create(@NotNull String path, @MagicConstant(flags = {ALLOW_FLOAT_SCALING, DARK, FIND_SVG}) int flags, @NotNull ScaleContext scaleContext) {
      // Prefer retina images for HiDPI scale, because downscaling
      // retina images provides a better result than up-scaling non-retina images.
      double pixScale = scaleContext.getScale(DerivedScaleType.PIX_SCALE);
      boolean retina = JBUIScale.isHiDPI(pixScale);

      ImageDescriptorListBuilder builder = new ImageDescriptorListBuilder(FileUtilRt.getNameWithoutExtension(path),
                                                                          FileUtilRt.getExtension(path),
                                                                          BitUtil.isSet(flags, FIND_SVG),
                                                                          adjustScaleFactor(BitUtil.isSet(flags, ALLOW_FLOAT_SCALING), pixScale));

      boolean isDark = BitUtil.isSet(flags, DARK);
      if (!path.startsWith("file:") && path.contains("://")) {
        builder.add(StringUtilRt.endsWithIgnoreCase(StringUtil.substringBeforeLast(path, "?"), ".svg") ? ImageType.SVG : ImageType.IMG);
      }
      else if (retina && isDark) {
        builder.add(true, true);
        builder.add(true, false); // fallback to non-dark
      }
      else if (isDark) {
        builder.add(false, true);
        builder.add(false, false); // fallback to non-dark
      }
      else if (retina) {
        builder.add(true, false);
      }
      else {
        builder.add(false, false);
      }
      return new ImageDescriptorList(builder.build());
    }
  }

  private interface ImageConverter {
    Image convert(@Nullable Image source, ImageDescriptor desc);
  }

  private static final class ImageConverterChain extends ArrayList<ImageConverter> {
    private ImageConverterChain() {}

    public static ImageConverterChain create() {
      return new ImageConverterChain();
    }

    ImageConverterChain withFilter(@Nullable ImageFilter[] filters) {
      if (filters == null) return this;
      ImageConverterChain chain = this;
      for (ImageFilter filter : filters) {
        chain = chain.withFilter(filter);
      }
      return chain;
    }

    ImageConverterChain withFilter(final ImageFilter filter) {
      if (filter == null) return this;
      return with(new ImageConverter() {
        @Override
        public Image convert(Image source, ImageDescriptor desc) {
          return ImageUtil.filter(source, filter);
        }
      });
    }

    ImageConverterChain withHiDPI(final ScaleContext ctx) {
      if (ctx == null) return this;
      return with(new ImageConverter() {
        @Override
        public Image convert(Image source, ImageDescriptor desc) {
          double usrScale = ctx.getScale(DerivedScaleType.EFF_USR_SCALE);
          return ImageUtil.ensureHiDPI(source, ctx,
                                       desc.origUsrSize.getWidth() * usrScale,
                                       desc.origUsrSize.getHeight() * usrScale);
        }
      });
    }

    public ImageConverterChain with(ImageConverter f) {
      add(f);
      return this;
    }

    public Image convert(Image image, ImageDescriptor desc) {
      for (ImageConverter f : this) {
        image = f.convert(image, desc);
      }
      return image;
    }
  }

  public static final Component ourComponent = new Component() {
  };

  @Nullable
  public static Image loadFromUrl(@NotNull URL url) {
    return loadFromUrl(url, true);
  }

  @Nullable
  public static Image loadFromUrl(@NotNull URL url, boolean allowFloatScaling) {
    return loadFromUrl(url, allowFloatScaling, true, new ImageFilter[] {null}, ScaleContext.create());
  }

  /**
   * @see #loadFromUrl(URL, Class, int, ImageFilter[], ScaleContext)
   */
  @Nullable
  public static Image loadFromUrl(@NotNull URL url, final boolean allowFloatScaling, boolean useCache, ImageFilter[] filters, final ScaleContext ctx) {
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
  @Nullable
  public static Image loadFromUrl(@NotNull URL url, @Nullable Class aClass, @MagicConstant(flags = {ALLOW_FLOAT_SCALING, USE_CACHE, DARK, FIND_SVG}) int flags, @Nullable ImageFilter[] filters, ScaleContext scaleContext) {
    return loadFromUrl(url.toString(), aClass, flags, filters, scaleContext);
  }

  /**
   * Loads an image of available resolution (1x, 2x, ...) and scales to address the provided scale context.
   * Then wraps the image with {@link JBHiDPIScaledImage} if necessary.
   */
  @Nullable
  public static Image loadFromUrl(@NotNull String path, @Nullable Class aClass, @MagicConstant(flags = {ALLOW_FLOAT_SCALING, USE_CACHE, DARK, FIND_SVG}) int flags, @Nullable ImageFilter[] filters, ScaleContext scaleContext) {
    // We can't check all 3rd party plugins and convince the authors to add @2x icons.
    // In IDE-managed HiDPI mode with scale > 1.0 we scale images manually.
    return ImageDescriptorList.create(path, flags, scaleContext).load(
      ImageConverterChain.create()
        .withFilter(filters)
        .with(new ImageConverter() {
          @Override
          public Image convert(Image source, ImageDescriptor desc) {
            if (source != null && desc.type != ImageType.SVG) {
              double scale = adjustScaleFactor(BitUtil.isSet(flags, ALLOW_FLOAT_SCALING), scaleContext.getScale(DerivedScaleType.PIX_SCALE));
              if (desc.scale > 1) scale /= desc.scale; // compensate the image original scale
              source = scaleImage(source, scale);
            }
            return source;
          }
        })
        .withHiDPI(scaleContext),
      BitUtil.isSet(flags, USE_CACHE), aClass);
  }

  private static double adjustScaleFactor(boolean allowFloatScaling, double scale) {
    return allowFloatScaling ? scale : JBUIScale.isHiDPI(scale) ? 2f : 1f;
  }

  @NotNull
  public static Image scaleImage(Image image, double scale) {
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
    Class callerClass = ReflectionUtil.getGrandCallerClass();
    if (callerClass == null) return null;
    return loadFromResource(s, callerClass);
  }

  @Nullable
  public static Image loadFromResource(@NonNls @NotNull String path, @NotNull Class aClass) {
    ScaleContext scaleContext = ScaleContext.create();
    int flags = FIND_SVG | ALLOW_FLOAT_SCALING;
    if (StartupUiUtil.isUnderDarcula()) {
      flags |= DARK;
    }
    return ImageDescriptorList.create(path, flags, scaleContext)
      .load(ImageConverterChain.create().withHiDPI(scaleContext), true, aClass);
  }

  public static Image loadFromBytes(@NotNull final byte[] bytes) {
    return loadFromStream(new ByteArrayInputStream(bytes));
  }

  public static Image loadFromStream(@NotNull final InputStream inputStream) {
    // for backward compatibility assume the image is hidpi-aware (includes default SYS_SCALE)
    return loadFromStream(inputStream, ScaleContext.create(), null);
  }

  @Deprecated
  public static Image loadFromStream(@NotNull final InputStream inputStream, final int scale) {
    return loadFromStream(inputStream, ScaleContext.create(ScaleType.OBJ_SCALE.of(scale)), null);
  }

  @Deprecated
  public static Image loadFromStream(@NotNull final InputStream inputStream, final int scale, ImageFilter filter) {
    return loadFromStream(inputStream, ScaleContext.create(ScaleType.OBJ_SCALE.of(scale)), filter);
  }

  /**
   * The scale context describes the image the stream presents.
   */
  public static Image loadFromStream(@NotNull InputStream inputStream, @NotNull ScaleContext ctx, ImageFilter filter) {
    try {
      ImageDescriptor desc = new ImageDescriptor(ctx.getScale(DerivedScaleType.PIX_SCALE));
      Image image = desc.loadFromStream(inputStream, null, null);
      return ImageConverterChain.create()
        .withFilter(filter)
        .withHiDPI(ctx)
        .convert(image, desc);
    }
    catch (IOException ex) {
      getLogger().error(ex);
    }
    return null;
  }

  public static @Nullable Image loadCustomIcon(@NotNull File f) throws IOException {
    final Image icon = _loadImageFromFile(f);
    if (icon == null)
      return null;

    final int w = icon.getWidth(null);
    final int h = icon.getHeight(null);

    if (w <= 0 || h <= 0) {
      getLogger().error("negative image size: w=" + w + ", h=" + h + ", path=" + f.getPath());
      return null;
    }

    if (w > EmptyIcon.ICON_18.getIconWidth() || h > EmptyIcon.ICON_18.getIconHeight()) {
      final double s = EmptyIcon.ICON_18.getIconWidth()/(double)Math.max(w, h);
      return scaleImage(icon, s);
    }

    return icon;
  }

  private static @Nullable Image _loadImageFromFile(@NotNull File f) throws IOException {
    final ScaleContext ctx = ScaleContext.create();
    final double scale = ctx.getScale(DerivedScaleType.PIX_SCALE); // probably, need implement naming conventions: filename ends with @2x => HiDPI (scale=2)
    ImageDescriptor desc = new ImageDescriptor(f.toURI().toURL().toString(), scale, StringUtilRt.endsWithIgnoreCase(f.getPath(), ".svg") ? ImageType.SVG : ImageType.IMG);
    return ImageUtil.ensureHiDPI(desc.load(true), ctx);
  }
}
