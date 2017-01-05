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
import com.intellij.util.ui.UIUtil;
import org.imgscalr.Scalr;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
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

public class ImageLoader implements Serializable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ImageLoader");

  private static final ConcurrentMap<String, Image> ourCache = ContainerUtil.createConcurrentSoftValueMap();

  private static class ImageDesc {
    public enum Type {
      PNG,

      SVG {
        @Override
        public Image load(URL url, InputStream is, float scale) throws IOException {
          return SVGLoader.load(url, is, scale);
        }
      },

      UNDEFINED;

      public Image load(URL url, InputStream stream, float scale) throws IOException {
        return ImageLoader.load(stream, (int)scale);
      }
    }

    public final String path;
    public final @Nullable Class cls; // resource class if present
    public final float scale; // initial scale factor
    public final Type type;
    public final boolean original; // path is not altered

    public ImageDesc(String path, Class cls, float scale, Type type) {
      this(path, cls, scale, type, false);
    }

    public ImageDesc(String path, Class cls, float scale, Type type, boolean original) {
      this.path = path;
      this.cls = cls;
      this.scale = scale;
      this.type = type;
      this.original = original;
    }

    @Nullable
    public Image load() throws IOException {
      String cacheKey = null;
      InputStream stream = null;
      URL url = null;
      if (cls != null) {
        //noinspection IOResourceOpenedButNotSafelyClosed
        stream = cls.getResourceAsStream(path);
        if (stream == null) return null;
      }
      if (stream == null) {
        cacheKey = path;
        Image image = ourCache.get(cacheKey);
        if (image != null) return image;

        url = new URL(path);
        URLConnection connection = url.openConnection();
        if (connection instanceof HttpURLConnection) {
          if (!original) return null;
          connection.addRequestProperty("User-Agent", "IntelliJ");
        }
        stream = connection.getInputStream();
      }
      Image image = type.load(url, stream, scale);
      if (image != null && cacheKey != null) {
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
      for (ImageDesc desc : this) {
        try {
          Image image = desc.load();
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
                                       boolean retina,
                                       boolean allowFloatScaling)
    {
      return create(file, cls, dark, retina, allowFloatScaling, JBUI.pixScale());
    }

    public static ImageDescList create(@NotNull String file,
                                       @Nullable Class cls,
                                       boolean dark,
                                       boolean retina,
                                       boolean allowFloatScaling,
                                       float pixScale)
    {
      ImageDescList vars = new ImageDescList();
      if (retina || dark) {
        final String name = FileUtil.getNameWithoutExtension(file);
        final String ext = FileUtilRt.getExtension(file);

        pixScale = adjustScaleFactor(allowFloatScaling, pixScale);

        if (Registry.is("ide.svg.icon") && dark) {
          vars.add(new ImageDesc(name + "_dark.svg", cls, pixScale, ImageDesc.Type.SVG));
        }

        if (Registry.is("ide.svg.icon")) {
          vars.add(new ImageDesc(name + ".svg", cls, pixScale, ImageDesc.Type.SVG));
        }

        if (dark && retina) {
          vars.add(new ImageDesc(name + "@2x_dark." + ext, cls, 2f, ImageDesc.Type.PNG));
        }

        if (dark) {
          vars.add(new ImageDesc(name + "_dark." + ext, cls, 1f, ImageDesc.Type.PNG));
        }

        if (retina) {
          vars.add(new ImageDesc(name + "@2x." + ext, cls, 2f, ImageDesc.Type.PNG));
        }
      }
      vars.add(new ImageDesc(file, cls, 1f, ImageDesc.Type.PNG, true));
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

    public ImageConverterChain withRetina() {
      return with(new ImageConverter() {
        @Override
        public Image convert(Image source, ImageDesc desc) {
          if (source != null && UIUtil.isJDKManagedHiDPI() && desc.scale > 1) {
            return RetinaImage.createFrom(source, (int)desc.scale, ourComponent);
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
    return loadFromUrl(url, allowFloatScaling, new ImageFilter[] {filter}, JBUI.pixScale());
  }

  /**
   * Loads an image by the passed url in scale (1x, 2x, ...) possibly closed to the passed JBUI pix scale,
   * then simply returns it in the JDK-managed HiDPI mode, otherwise scales the image
   * according to the passed scale and returns.
   */
  @Nullable
  public static Image loadFromUrl(@NotNull URL url, boolean allowFloatScaling, ImageFilter[] filters, float pixScale) {
    final float scaleFactor = adjustScaleFactor(allowFloatScaling, pixScale); // valid for Retina as well

    // We can't check all 3rd party plugins and convince the authors to add @2x icons.
    // (scaleFactor > 1.0) != isJDKManagedHiDPI() => we should scale images manually.
    // Note we never scale images on JDKManagedHiDPI displays because scaling is handled by the system.

    final boolean scaleImages = (scaleFactor > 1.0f && !UIUtil.isJDKManagedHiDPI());

    // For any scale factor > 1.0, always prefer retina images, because downscaling
    // retina images provides a better result than upscaling non-retina images.
    final boolean loadRetinaImages = (scaleFactor > 1.0f);

    return ImageDescList.create(url.toString(), null, UIUtil.isUnderDarcula(), loadRetinaImages, allowFloatScaling, pixScale).load(
      ImageConverterChain.create().
        withFilter(filters).
        withRetina().
        with(new ImageConverter() {
              public Image convert(Image source, ImageDesc desc) {
                if (source != null && scaleImages && desc.type != ImageDesc.Type.SVG) {
                  if (desc.path.contains("@2x"))
                    return scaleImage(source, scaleFactor / 2.0f);  // divide by 2.0 as Retina images are 2x the resolution.
                  else
                    return scaleImage(source, scaleFactor);
                }
                return source;
              }
        }));
  }

  private static float adjustScaleFactor(boolean allowFloatScaling, float scale) {
    return allowFloatScaling ? scale : scale > 1.5f ? 2f : 1f;
  }

  @NotNull
  public static Image scaleImage(Image image, float scale) {
    if (scale == 1.0) return image;

    if (image instanceof JBHiDPIScaledImage) {
      return ((JBHiDPIScaledImage)image).scale(scale);
    }
    int w = image.getWidth(null);
    int h = image.getHeight(null);
    if (w <= 0 || h <= 0) {
      return image;
    }
    int width = (int)(scale * w);
    int height = (int)(scale * h);
    // Using "QUALITY" instead of "ULTRA_QUALITY" results in images that are less blurry
    // because ultra quality performs a few more passes when scaling, which introduces blurriness
    // when the scaling factor is relatively small (i.e. <= 3.0f) -- which is the case here.
    return Scalr.resize(ImageUtil.toBufferedImage(image), Scalr.Method.QUALITY, width, height);
  }

  @Nullable
  public static Image loadFromUrl(URL url, boolean dark, boolean retina) {
    return loadFromUrl(url, dark, retina, (ImageFilter[])null);
  }

  @Nullable
  public static Image loadFromUrl(URL url, boolean dark, boolean retina, ImageFilter filter) {
    return loadFromUrl(url, dark, retina, new ImageFilter[] {filter});
  }

  @Nullable
  public static Image loadFromUrl(URL url, boolean dark, boolean retina, ImageFilter[] filters) {
    return ImageDescList.create(url.toString(), null, dark, retina, true).
      load(ImageConverterChain.create().withFilter(filters).withRetina());
  }

  @Nullable
  public static Image loadFromResource(@NonNls @NotNull String s) {
    Class callerClass = ReflectionUtil.getGrandCallerClass();
    if (callerClass == null) return null;
    return loadFromResource(s, callerClass);
  }

  @Nullable
  public static Image loadFromResource(@NonNls @NotNull String path, @NotNull Class aClass) {
    return ImageDescList.create(path, aClass, UIUtil.isUnderDarcula(), JBUI.pixScale() >= 1.5f, true).
      load(ImageConverterChain.create().withRetina());
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
    return ImageConverterChain.create().withFilter(filter).withRetina().convert(image, desc);
  }

  private static Image load(@NotNull final InputStream inputStream, final int scale) {
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
