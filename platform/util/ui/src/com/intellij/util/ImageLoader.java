// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.StartupUiUtil;
import org.apache.xmlgraphics.java2d.Dimension2DDouble;
import org.imgscalr.Scalr;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.Dimension2D;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageFilter;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import static com.intellij.ui.scale.DerivedScaleType.EFF_USR_SCALE;
import static com.intellij.ui.scale.DerivedScaleType.PIX_SCALE;
import static com.intellij.ui.scale.ScaleType.OBJ_SCALE;
import static com.intellij.util.ImageLoader.ImageDesc.Type.IMG;
import static com.intellij.util.ImageLoader.ImageDesc.Type.SVG;

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
  private static final ConcurrentMap<String, Pair<Image, Dimension2D>> ourCache = ContainerUtil.createConcurrentSoftValueMap();

  public static void clearCache() {
    ourCache.clear();
  }


  @SuppressWarnings("UnusedDeclaration") // set from com.intellij.internal.IconsLoadTime
  private static LoadFunction measureLoad;

  /**
   * For internal usage.
   */
  public interface LoadFunction {
    Image load(@Nullable LoadFunction delegate, @NotNull ImageDesc.Type type) throws IOException;
  }

  public static final class ImageDesc {
    public enum Type {IMG, SVG}

    final @NotNull String path;
    final double scale; // initial scale factor
    final @NotNull Type type;
    final boolean original; // path is not altered
    // The original user space size of the image. In case of SVG it's the size specified in the SVG doc.
    // Otherwise it's the size of the original image divided by the image's scale (defined by the extension @2x).
    final @NotNull Dimension2D origUsrSize;

    public ImageDesc(@NotNull String path, double scale, @NotNull Type type) {
      this(path, scale, type, false);
    }

    public ImageDesc(double scale) {
      this("", scale, IMG, false);
    }

    private ImageDesc(@NotNull String path, double scale, @NotNull Type type, boolean original) {
      this.path = path;
      this.scale = scale;
      this.type = type;
      this.original = original;
      this.origUsrSize = new Dimension2DDouble(0, 0);
    }

    @Nullable
    public Image load(boolean useCache) throws IOException {
      return load(useCache, null);
    }

    @Nullable
    public Image load(boolean useCache, @Nullable Class<?> resourceClass) throws IOException {
      if (StringUtil.isEmpty(path)) {
        getLogger().warn("empty image path", new Throwable());
        return null;
      }

      InputStream stream = null;

      boolean isFromFile = path.startsWith("file:");
      if (!isFromFile && resourceClass != null) {
        //noinspection IOResourceOpenedButNotSafelyClosed
        stream = resourceClass.getResourceAsStream(path);
        if (stream == null) {
          return null;
        }
      }

      String cacheKey = null;
      URL url = null;
      if (stream == null) {
        if (useCache) {
          cacheKey = path + (type == SVG ? "_@" + scale + "x" : "");
          Pair<Image, Dimension2D> pair = ourCache.get(cacheKey);
          if (pair != null) {
            origUsrSize.setSize(pair.second);
            return pair.first;
          }
        }

        url = new URL(path);
        if (isFromFile && !SystemInfoRt.isWindows) {
          byte[] bytes = Files.readAllBytes(Paths.get(url.getPath()));
          //noinspection IOResourceOpenedButNotSafelyClosed
          stream = new BufferExposingByteArrayInputStream(bytes);
        }
        else {
          URLConnection connection = url.openConnection();
          if (connection instanceof HttpURLConnection) {
            if (!original) return null;
            connection.addRequestProperty("User-Agent", "IntelliJ");
          }
          stream = connection.getInputStream();
        }
      }

      return loadFromStream(stream, url, cacheKey);
    }

    @Nullable
    public Image loadFromStream(@NotNull InputStream stream, @Nullable URL url, @Nullable String cacheKey) throws IOException {
      Image image = loadImpl(url, stream);
      if (image != null && cacheKey != null && 4L * image.getWidth(null) * image.getHeight(null) <= CACHED_IMAGE_MAX_SIZE) {
        ourCache.put(cacheKey, Pair.create(image, origUsrSize));
      }
      return image;
    }

    @Nullable
    private Image loadImpl(@Nullable URL url, @NotNull InputStream stream) throws IOException {
      LoadFunction f = new LoadFunction() {
        @Override
        public Image load(@Nullable LoadFunction delegate, @NotNull Type type) throws IOException {
          switch (type) {
            case SVG:
              return SVGLoader.load(url, stream, scale, origUsrSize);
            case IMG: {
              return loadImpl(stream);
            }
          }
          return null;
        }
      };
      if (measureLoad != null) {
        return measureLoad.load(f, type);
      }
      return f.load(null, type);
    }

    @Nullable
    private Image loadImpl(@NotNull InputStream stream) {
      try {
        Image image;
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        if (stream instanceof BufferExposingByteArrayInputStream) {
          BufferExposingByteArrayInputStream byteInput = (BufferExposingByteArrayInputStream)stream;
          image = toolkit.createImage(byteInput.getInternalBuffer(), 0, byteInput.available());
        }
        else {
          BufferExposingByteArrayOutputStream outputStream = new BufferExposingByteArrayOutputStream();
          try {
            FileUtilRt.copy(stream, outputStream);
          }
          finally {
            stream.close();
          }
          image = toolkit.createImage(outputStream.getInternalBuffer(), 0, outputStream.size());
        }

        waitForImage(image);
        origUsrSize.setSize(image.getWidth(null) / scale, image.getHeight(null) / scale);
        return image;
      }
      catch (Exception ex) {
        getLogger().error(ex);
      }
      return null;
    }

    @Override
    public String toString() {
      return path + ", scale: " + scale + ", type: " + type;
    }
  }

  private static final class ImageDescriptorListBuilder {
    private final List<ImageDesc> list = new SmartList<>();
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
        add(retina, dark, SVG);
      }
      add(retina, dark, IMG);
    }

    void add(boolean retina, boolean dark, ImageDesc.Type type) {
      String _ext = SVG == type ? "svg" : ext;
      double _scale = SVG == type ? scale : retina ? 2 : 1;

      list.add(new ImageDesc(name + (dark ? "_dark" : "") + (retina ? "@2x" : "") + "." + _ext, _scale, type));
      if (retina && dark) {
        list.add(new ImageDesc(name + "@2x_dark" + "." + _ext, _scale, type));
      }
      if (retina) {
        // a fallback to 1x icon
        list.add(new ImageDesc(name + (dark ? "_dark" : "") + "." + _ext, SVG == type ? scale : 1, type));
      }
    }

    void add(ImageDesc.Type type) {
      list.add(new ImageDesc(name + "." + ext, 1.0, type, true));
    }

    @NotNull
    List<ImageDesc> build() {
      return list;
    }
  }

  private static final class ImageDescriptorList {
    private final List<ImageDesc> list;

    private ImageDescriptorList(@NotNull List<ImageDesc> list) {
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
      for (ImageDesc descriptor : list) {
        try {
          Image image = descriptor.load(useCache, resourceClass);
          if (image == null) {
            continue;
          }

          getLogger().debug("Loaded image: " + descriptor);
          return converters.convert(image, descriptor);
        }
        catch (IOException ignore) {
        }
      }
      return null;
    }

    @NotNull
    public static ImageDescriptorList create(@NotNull String path, @MagicConstant(flags = {ALLOW_FLOAT_SCALING, DARK, FIND_SVG}) int flags, @NotNull ScaleContext scaleContext) {
      // Prefer retina images for HiDPI scale, because downscaling
      // retina images provides a better result than up-scaling non-retina images.
      double pixScale = scaleContext.getScale(PIX_SCALE);
      boolean retina = JBUIScale.isHiDPI(pixScale);

      ImageDescriptorListBuilder builder = new ImageDescriptorListBuilder(FileUtilRt.getNameWithoutExtension(path),
                                                                          FileUtilRt.getExtension(path),
                                                                          BitUtil.isSet(flags, FIND_SVG),
                                                                          adjustScaleFactor(BitUtil.isSet(flags, ALLOW_FLOAT_SCALING), pixScale));

      boolean isDark = BitUtil.isSet(flags, DARK);
      if (!path.startsWith("file:") && path.contains("://")) {
        builder.add(StringUtilRt.endsWithIgnoreCase(StringUtil.substringBeforeLast(path, "?"), ".svg") ? SVG : IMG);
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
    Image convert(@Nullable Image source, ImageDesc desc);
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
        public Image convert(Image source, ImageDesc desc) {
          return ImageUtil.filter(source, filter);
        }
      });
    }

    ImageConverterChain withHiDPI(final ScaleContext ctx) {
      if (ctx == null) return this;
      return with(new ImageConverter() {
        @Override
        public Image convert(Image source, ImageDesc desc) {
          double usrScale = ctx.getScale(EFF_USR_SCALE);
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

    public Image convert(Image image, ImageDesc desc) {
      for (ImageConverter f : this) {
        image = f.convert(image, desc);
      }
      return image;
    }
  }

  public static final Component ourComponent = new Component() {
  };

  @SuppressWarnings("UnusedReturnValue")
  private static boolean waitForImage(Image image) {
    if (image == null) return false;
    if (image.getWidth(null) > 0) return true;
    MediaTracker mediatracker = new MediaTracker(ourComponent);
    mediatracker.addImage(image, 1);
    try {
      mediatracker.waitForID(1, 5000);
    }
    catch (InterruptedException ex) {
      getLogger().info(ex);
    }
    return !mediatracker.isErrorID(1);
  }

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
          public Image convert(Image source, ImageDesc desc) {
            if (source != null && desc.type != SVG) {
              double scale = adjustScaleFactor(BitUtil.isSet(flags, ALLOW_FLOAT_SCALING), scaleContext.getScale(PIX_SCALE));
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
    return loadFromStream(inputStream, ScaleContext.create(OBJ_SCALE.of(scale)), null);
  }

  @Deprecated
  public static Image loadFromStream(@NotNull final InputStream inputStream, final int scale, ImageFilter filter) {
    return loadFromStream(inputStream, ScaleContext.create(OBJ_SCALE.of(scale)), filter);
  }

  /**
   * The scale context describes the image the stream presents.
   */
  public static Image loadFromStream(@NotNull InputStream inputStream, @NotNull ScaleContext ctx, ImageFilter filter) {
    try {
      ImageDesc desc = new ImageDesc(ctx.getScale(PIX_SCALE));
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
    final double scale = ctx.getScale(PIX_SCALE); // probably, need implement naming conventions: filename ends with @2x => HiDPI (scale=2)
    ImageDesc desc = new ImageDesc(f.toURI().toURL().toString(), scale, StringUtilRt.endsWithIgnoreCase(f.getPath(), ".svg") ? SVG : IMG);
    return ImageUtil.ensureHiDPI(desc.load(true), ctx);
  }
}
