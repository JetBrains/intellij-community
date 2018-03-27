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
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBUI.ScaleContext;
import com.intellij.util.ui.UIUtil;
import org.imgscalr.Scalr;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import static com.intellij.util.ui.JBUI.ScaleType.*;

public class ImageLoader implements Serializable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ImageLoader");

  public static final int CACHED_IMAGE_MAX_SIZE = (int)Math.round(Registry.doubleValue("ide.cached.image.max.size") * 1024 * 1024);
  private static final ConcurrentMap<String, Image> ourCache = ContainerUtil.createConcurrentSoftValueMap();

  @SuppressWarnings({"UnusedDeclaration"}) // set from com.intellij.internal.IconsLoadTime
  private static LoadFunction measureLoad;

  /**
   * For internal usage.
   */
  public interface LoadFunction {
    Image load(@Nullable LoadFunction delegate) throws IOException;
  }

  private static class ImageDesc {
    public enum Type {
      PNG,

      SVG {
        @Override
        public Image load(final URL url, final InputStream is, final double scale) throws IOException {
          LoadFunction f = new LoadFunction() {
            @Override
            public Image load(LoadFunction delegate) throws IOException {
              return SVGLoader.load(url, is, scale);
            }
          };
          if (measureLoad != null && Registry.is("ide.svg.icon")) {
            return measureLoad.load(f);
          }
          return f.load(null);
        }
      },

      UNDEFINED;

      public Image load(final URL url, final InputStream is, final double scale) throws IOException {
        LoadFunction f = new LoadFunction() {
          @Override
          public Image load(LoadFunction delegate) {
            return ImageLoader.load(is, scale);
          }
        };
        if (measureLoad != null && !Registry.is("ide.svg.icon")) {
          return measureLoad.load(f);
        }
        return f.load(null);
      }
    }

    public final String path;
    public final @Nullable Class cls; // resource class if present
    public final double scale; // initial scale factor
    public final Type type;
    public final boolean original; // path is not altered

    public ImageDesc(String path, Class cls, double scale, Type type) {
      this(path, cls, scale, type, false);
    }

    public ImageDesc(String path, Class cls, double scale, Type type, boolean original) {
      this.path = path;
      this.cls = cls;
      this.scale = scale;
      this.type = type;
      this.original = original;
    }

    @Nullable
    public Image load() throws IOException {
      return  load(true);
    }

    @Nullable
    public Image load(boolean useCache) throws IOException {
      String cacheKey = null;
      InputStream stream = null;
      URL url = null;
      if (cls != null) {
        //noinspection IOResourceOpenedButNotSafelyClosed
        stream = cls.getResourceAsStream(path);
        if (stream == null) return null;
      }
      if (stream == null) {
        if (useCache) {
          cacheKey = path + (type == Type.SVG ? "_@" + scale + "x" : "");
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
      Image image = type.load(url, stream, scale);
      if (image != null && cacheKey != null &&
          image.getWidth(null) * image.getHeight(null) * 4 <= CACHED_IMAGE_MAX_SIZE)
      {
        ourCache.put(cacheKey, image);
      }
      return image;
    }

    @Override
    public String toString() {
      return path + ", scale: " + scale + ", type: " + type;
    }
  }

  private static class ImageDescList extends ArrayList<ImageDesc> {
    private ImageDescList() {}

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

    public static ImageDescList create(@NotNull String file,
                                       @Nullable Class cls,
                                       boolean dark,
                                       boolean allowFloatScaling,
                                       ScaleContext ctx)
    {
      ImageDescList vars = new ImageDescList();

      boolean ideSvgIconSupport = Registry.is("ide.svg.icon");

      // Prefer retina images for HiDPI scale, because downscaling
      // retina images provides a better result than upscaling non-retina images.
      boolean retina = JBUI.isHiDPI(ctx.getScale(PIX_SCALE));

      if (retina || dark || ideSvgIconSupport) {
        final String name = FileUtil.getNameWithoutExtension(file);
        final String ext = FileUtilRt.getExtension(file);

        double scale = adjustScaleFactor(allowFloatScaling, ctx.getScale(PIX_SCALE));

        if (ideSvgIconSupport && dark) {
          vars.add(new ImageDesc(name + "_dark.svg", cls, scale, ImageDesc.Type.SVG));
        }

        if (ideSvgIconSupport) {
          vars.add(new ImageDesc(name + ".svg", cls, scale, ImageDesc.Type.SVG));
        }

        if (dark && retina) {
          vars.add(new ImageDesc(name + "@2x_dark." + ext, cls, 2d, ImageDesc.Type.PNG));
        }

        if (dark) {
          vars.add(new ImageDesc(name + "_dark." + ext, cls, 1d, ImageDesc.Type.PNG));
        }

        if (retina) {
          vars.add(new ImageDesc(name + "@2x." + ext, cls, 2d, ImageDesc.Type.PNG));
        }
      }
      vars.add(new ImageDesc(file, cls, 1d, ImageDesc.Type.PNG, true));
      return vars;
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

    public ImageConverterChain withFilter(final ImageFilter[] filters) {
      ImageConverterChain chain = this;
      for (ImageFilter filter : filters) {
        chain = chain.withFilter(filter);
      }
      return chain;
    }

    public ImageConverterChain withFilter(final ImageFilter filter) {
      return with(new ImageConverter() {
        @Override
        public Image convert(Image source, ImageDesc desc) {
          return ImageUtil.filter(source, filter);
        }
      });
    }

    public ImageConverterChain withHiDPI(final ScaleContext ctx) {
      return with(new ImageConverter() {
        @Override
        public Image convert(Image source, ImageDesc desc) {
          if (source != null && UIUtil.isJreHiDPI(ctx)) {
            return RetinaImage.createFrom(source, ctx.getScale(SYS_SCALE), ourComponent);
          }
          return source;
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
    return loadFromUrl(url, allowFloatScaling, (ImageFilter)null);
  }

  @Nullable
  public static Image loadFromUrl(@NotNull URL url, boolean allowFloatScaling, ImageFilter filter) {
    return loadFromUrl(url, allowFloatScaling, true, new ImageFilter[] {filter}, ScaleContext.create());
  }

  /**
   * Loads an image of available resolution (1x, 2x, ...) and scales to address the provided scale context.
   * Then wraps the image with {@link JBHiDPIScaledImage} if necessary.
   */
  @Nullable
  public static Image loadFromUrl(@NotNull URL url, final boolean allowFloatScaling, boolean useCache, ImageFilter[] filters, final ScaleContext ctx) {
    // We can't check all 3rd party plugins and convince the authors to add @2x icons.
    // In IDE-managed HiDPI mode with scale > 1.0 we scale images manually.

    return ImageDescList.create(url.toString(), null, UIUtil.isUnderDarcula(), allowFloatScaling, ctx).load(
      ImageConverterChain.create().
        withFilter(filters).
        with(new ImageConverter() {
              public Image convert(Image source, ImageDesc desc) {
                if (source != null && desc.type != ImageDesc.Type.SVG) {
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
    ImageDesc desc = new ImageDesc("", null, scale, ImageDesc.Type.UNDEFINED);
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

  public static boolean isGoodSize(final Icon icon) {
    return IconLoader.isGoodSize(icon);
  }

  /**
   * @deprecated use {@link ImageDescList}
   */
  public static List<Pair<String, Integer>> getFileNames(@NotNull String file) {
    return getFileNames(file, false, false);
  }

  /**
   * @deprecated use {@link ImageDescList}
   */
  public static List<Pair<String, Integer>> getFileNames(@NotNull String file, boolean dark, boolean retina) {
    new UnsupportedOperationException("unsupported method").printStackTrace();
    return new ArrayList<Pair<String, Integer>>();
  }
}
