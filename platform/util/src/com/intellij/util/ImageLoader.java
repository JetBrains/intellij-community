/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBUI.ScaleContext;
import com.intellij.util.ui.UIUtil;
import org.imgscalr.Scalr;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentMap;

import static com.intellij.util.ImageLoader.ImageDesc.Type.IMG;
import static com.intellij.util.ImageLoader.ImageDesc.Type.SVG;
import static com.intellij.util.ui.JBUI.ScaleType.PIX_SCALE;

public class ImageLoader implements Serializable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ImageLoader");

  public static final long CACHED_IMAGE_MAX_SIZE = (long)(Registry.doubleValue("ide.cached.image.max.size") * 1024 * 1024);
  private static final ConcurrentMap<String, Image> ourCache = ContainerUtil.createConcurrentSoftValueMap();

  @SuppressWarnings("UnusedDeclaration") // set from com.intellij.internal.IconsLoadTime
  private static LoadFunction measureLoad;

  /**
   * For internal usage.
   */
  public interface LoadFunction {
    Image load(@Nullable LoadFunction delegate, @NotNull ImageDesc.Type type) throws IOException;
  }

  public static class ImageDesc {
    public enum Type {IMG, SVG}

    final String path;
    final Class cls; // resource class if present
    final double scale; // initial scale factor
    final Type type;
    final boolean original; // path is not altered

    ImageDesc(@NotNull String path, @Nullable Class cls, double scale, @NotNull Type type) {
      this(path, cls, scale, type, false);
    }

    ImageDesc(@NotNull String path, @Nullable Class cls, double scale, @NotNull Type type, boolean original) {
      this.path = path;
      this.cls = cls;
      this.scale = scale;
      this.type = type;
      this.original = original;
    }

    @Nullable
    public Image load() throws IOException {
      return load(true);
    }

    @Nullable
    public Image load(boolean useCache) throws IOException {
      InputStream stream = null;
      if (cls != null) {
        //noinspection IOResourceOpenedButNotSafelyClosed
        stream = cls.getResourceAsStream(path);
        if (stream == null) return null;
      }
      String cacheKey = null;
      URL url = null;
      if (stream == null) {
        if (useCache) {
          cacheKey = path + (type == SVG ? "_@" + scale + "x" : "");
          Image image = ourCache.get(cacheKey);
          if (image != null) return image;
        }
        url = new URL(path);
        URLConnection connection = url.openConnection();
        if (connection instanceof HttpURLConnection) {
          if (!original) return null;
          connection.addRequestProperty("User-Agent", "IntelliJ");
        }
        stream = connection.getInputStream();
      }
      Image image = loadImpl(url, stream, scale);
      if (image != null && cacheKey != null &&
          image.getWidth(null) * image.getHeight(null) * 4 <= CACHED_IMAGE_MAX_SIZE)
      {
        ourCache.put(cacheKey, image);
      }
      return image;
    }

    Image loadImpl(final URL url, final InputStream stream, final double scale) throws IOException {
      LoadFunction f = new LoadFunction() {
        @Override
        public Image load(@Nullable LoadFunction delegate, @NotNull Type type) throws IOException {
          switch (type) {
            case SVG:
              return SVGLoader.load(url, stream, ImageDesc.this.scale);
            case IMG:
              return ImageLoader.load(stream, scale);
          }
          return null;
        }
      };
      if (measureLoad != null) {
        return measureLoad.load(f, type);
      }
      return f.load(null, type);
    }

    @Override
    public String toString() {
      return path + ", scale: " + scale + ", type: " + type;
    }
  }

  private static class ImageDescList extends ArrayList<ImageDesc> {
    private ImageDescList() {}

    static class Builder {
      final ImageDescList list = new ImageDescList();
      final String name;
      final String ext;
      final Class cls;
      final boolean svg;
      final double scale;

      Builder(String name, String ext, Class cls, boolean svg, double scale) {
        this.name = name;
        this.ext = ext;
        this.cls = cls;
        this.svg = svg;
        this.scale = scale;
      }

      void add(boolean retina, boolean dark) {
        if (svg) add(retina, dark, SVG);
        add(retina, dark, IMG);
      }

      void add(boolean retina, boolean dark, ImageDesc.Type type) {
        String _ext = SVG == type ? "svg" : ext;
        double _scale = SVG == type ? scale : retina ? 2 : 1;
        if (SVG == type && retina) _scale /= 2d;

        list.add(new ImageDesc(name + (dark ? "_dark" : "") + (retina ? "@2x" : "") + "." + _ext, cls, _scale, type));
        if (retina && dark) {
          list.add(new ImageDesc(name + "@2x_dark" + "." + _ext, cls, _scale, type));
        }
        if (retina) {
          // a fallback to 1x icon
          list.add(new ImageDesc(name + (dark ? "_dark" : "") + "." + _ext, cls, SVG == type ? scale : 1, type));
        }
      }

      void add(ImageDesc.Type type) {
        list.add(new ImageDesc(name + "." + ext, cls, 1.0, type, true));
      }

      ImageDescList build() {
        return list;
      }
    }

    @Nullable
    public Image load() {
      return load(ImageConverterChain.create());
    }

    @Nullable
    public Image load(@NotNull ImageConverterChain converters) {
      return load(converters, true);
    }

    @Nullable
    public Image load(@NotNull ImageConverterChain converters, boolean useCache) {
      for (ImageDesc desc : this) {
        try {
          Image image = desc.load(useCache);
          if (image == null) continue;
          LOG.debug("Loaded image: " + desc);
          return converters.convert(image, desc);
        }
        catch (IOException ignore) {
        }
      }
      return null;
    }

    public static ImageDescList create(@NotNull String path,
                                       @Nullable Class cls,
                                       boolean dark,
                                       boolean allowFloatScaling,
                                       ScaleContext ctx)
    {
      // Prefer retina images for HiDPI scale, because downscaling
      // retina images provides a better result than up-scaling non-retina images.
      boolean retina = JBUI.isHiDPI(ctx.getScale(PIX_SCALE));

      Builder list = new Builder(FileUtil.getNameWithoutExtension(path),
                                 FileUtilRt.getExtension(path),
                                 cls,
                                 Registry.is("ide.svg.icon"),
                                 adjustScaleFactor(allowFloatScaling, ctx.getScale(PIX_SCALE)));

      if (path.contains("://") && !path.startsWith("file:")) {
        list.add(StringUtil.endsWithIgnoreCase(path, ".svg") ? SVG : IMG);
      }
      else if (retina && dark) {
        list.add(true, true);
        list.add(true, false); // fallback to non-dark
      }
      else if (dark) {
        list.add(false, true);
        list.add(false, false); // fallback to non-dark
      }
      else if (retina) {
        list.add(true, false);
      }
      else {
        list.add(false, false);
      }

      return list.build();
    }
  }

  private interface ImageConverter {
    Image convert(@Nullable Image source, ImageDesc desc);
  }

  private static class ImageConverterChain extends ArrayList<ImageConverter> {
    private ImageConverterChain() {}

    public static ImageConverterChain create() {
      return new ImageConverterChain();
    }

    ImageConverterChain withFilter(final ImageFilter[] filters) {
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
          return ImageUtil.ensureHiDPI(source, ctx);
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

  private static boolean waitForImage(Image image) {
    if (image == null) return false;
    if (image.getWidth(null) > 0) return true;
    MediaTracker mediatracker = new MediaTracker(ourComponent);
    mediatracker.addImage(image, 1);
    try {
      mediatracker.waitForID(1, 5000);
    }
    catch (InterruptedException ex) {
      LOG.info(ex);
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
   * @see #loadFromUrl(URL, boolean, boolean, boolean, ImageFilter[], ScaleContext)
   */
  @Nullable
  public static Image loadFromUrl(@NotNull URL url, final boolean allowFloatScaling, boolean useCache, ImageFilter[] filters, final ScaleContext ctx) {
    return loadFromUrl(url, allowFloatScaling, useCache, UIUtil.isUnderDarcula(), filters, ctx);
  }

  /**
   * Loads an image of available resolution (1x, 2x, ...) and scales to address the provided scale context.
   * Then wraps the image with {@link JBHiDPIScaledImage} if necessary.
   */
  @Nullable
  public static Image loadFromUrl(@NotNull URL url, final boolean allowFloatScaling, boolean useCache, boolean dark, ImageFilter[] filters, final ScaleContext ctx) {
    // We can't check all 3rd party plugins and convince the authors to add @2x icons.
    // In IDE-managed HiDPI mode with scale > 1.0 we scale images manually.

    return ImageDescList.create(url.toString(), null, dark, allowFloatScaling, ctx).load(
      ImageConverterChain.create().
        withFilter(filters).
        with(new ImageConverter() {
              @Override
              public Image convert(Image source, ImageDesc desc) {
                if (source != null && desc.type != SVG) {
                  double scale = adjustScaleFactor(allowFloatScaling, ctx.getScale(PIX_SCALE));
                  if (desc.scale > 1) scale /= desc.scale; // compensate the image original scale
                  source = scaleImage(source, scale);
                }
                return source;
              }
        }).
        withHiDPI(ctx),
      useCache);
  }

  private static double adjustScaleFactor(boolean allowFloatScaling, double scale) {
    return allowFloatScaling ? scale : JBUI.isHiDPI(scale) ? 2f : 1f;
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

  @Nullable
  public static Image loadFromResource(@NonNls @NotNull String s) {
    Class callerClass = ReflectionUtil.getGrandCallerClass();
    if (callerClass == null) return null;
    return loadFromResource(s, callerClass);
  }

  @Nullable
  public static Image loadFromResource(@NonNls @NotNull String path, @NotNull Class aClass) {
    ScaleContext ctx = ScaleContext.create();
    return ImageDescList.create(path, aClass, UIUtil.isUnderDarcula(), true, ctx).
      load(ImageConverterChain.create().withHiDPI(ctx));
  }

  public static Image loadFromStream(@NotNull final InputStream inputStream) {
    return loadFromStream(inputStream, 1);
  }

  public static Image loadFromStream(@NotNull final InputStream inputStream, final int scale) {
    return loadFromStream(inputStream, scale, null);
  }

  public static Image loadFromStream(@NotNull final InputStream inputStream, final int scale, ImageFilter filter) {
    Image image = load(inputStream, scale);
    ImageDesc desc = new ImageDesc("", null, scale, IMG);
    return ImageConverterChain.create().withFilter(filter).withHiDPI(ScaleContext.create()).convert(image, desc);
  }

  private static Image load(@NotNull final InputStream inputStream, double scale) {
    if (scale <= 0) throw new IllegalArgumentException("Scale must be 1 or greater");
    try {
      BufferExposingByteArrayOutputStream outputStream = new BufferExposingByteArrayOutputStream();
      try {
        byte[] buffer = new byte[1024];
        while (true) {
          final int n = inputStream.read(buffer);
          if (n < 0) break;
          outputStream.write(buffer, 0, n);
        }
      }
      finally {
        inputStream.close();
      }

      Image image = Toolkit.getDefaultToolkit().createImage(outputStream.getInternalBuffer(), 0, outputStream.size());

      waitForImage(image);

      return image;
    }
    catch (Exception ex) {
      LOG.error(ex);
    }

    return null;
  }
}
