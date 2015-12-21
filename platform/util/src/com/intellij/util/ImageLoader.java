/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Deprecated
public class ImageLoader implements Serializable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ImageLoader");

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

    public ImageDesc(String path, Class cls, float scale, Type type) {
      this.path = path;
      this.cls = cls;
      this.scale = scale;
      this.type = type;
    }

    @Nullable
    public Image load() throws IOException {
      InputStream stream = null;
      URL url = null;
      if (cls != null) {
        stream = cls.getResourceAsStream(path);
        if (stream == null) return null;
      }
      if (stream == null) {
        url = new URL(path);
        stream = url.openStream();
      }
      return type.load(url, stream, scale);
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
      ImageDescList vars = new ImageDescList();
      if (retina || dark) {
        final String name = FileUtil.getNameWithoutExtension(file);
        final String ext = FileUtilRt.getExtension(file);

        float scale = calcScaleFactor(allowFloatScaling);

        // TODO: allow SVG images to freely scale on Retina

        if (Registry.is("ide.svg.icon") && dark) {
          vars.add(new ImageDesc(name + "_dark.svg", cls, UIUtil.isRetina() ? 2f : scale, ImageDesc.Type.SVG));
        }

        if (Registry.is("ide.svg.icon")) {
          vars.add(new ImageDesc(name + ".svg", cls, UIUtil.isRetina() ? 2f : scale, ImageDesc.Type.SVG));
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
      vars.add(new ImageDesc(file, cls, 1f, ImageDesc.Type.PNG));
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
          if (source != null && UIUtil.isRetina() && desc.scale > 1) {
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
    return loadFromUrl(url, allowFloatScaling, null);
  }

  @Nullable
  public static Image loadFromUrl(@NotNull URL url, boolean allowFloatScaling, ImageFilter filter) {
    final float scaleFactor = calcScaleFactor(allowFloatScaling);

    // We can't check all 3rd party plugins and convince the authors to add @2x icons.
    // (scaleFactor > 1.0) != isRetina() => we should scale images manually.
    // Note we never scale images on Retina displays because scaling is handled by the system.
    final boolean scaleImages = (scaleFactor > 1.0f) && !UIUtil.isRetina();

    // For any scale factor > 1.0, always prefer retina images, because downscaling
    // retina images provides a better result than upscaling non-retina images.
    final boolean loadRetinaImages = UIUtil.isRetina() || scaleImages;

    return ImageDescList.create(url.toString(), null, UIUtil.isUnderDarcula(), loadRetinaImages, allowFloatScaling).load(
      ImageConverterChain.create().
        withFilter(filter).
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

  private static float calcScaleFactor(boolean allowFloatScaling) {
    float scaleFactor = allowFloatScaling ? JBUI.scale(1f) : JBUI.scale(1f) > 1.5f ? 2f : 1f;
    assert scaleFactor >= 1.0f : "By design, only scale factors >= 1.0 are supported";
    return scaleFactor;
  }

  @NotNull
  private static Image scaleImage(Image image, float scale) {
    int width = (int)(scale * image.getWidth(null));
    int height = (int)(scale * image.getHeight(null));
    // Using "QUALITY" instead of "ULTRA_QUALITY" results in images that are less blurry
    // because ultra quality performs a few more passes when scaling, which introduces blurriness
    // when the scaling factor is relatively small (i.e. <= 3.0f) -- which is the case here.
    return Scalr.resize(ImageUtil.toBufferedImage(image), Scalr.Method.QUALITY, width, height);
  }

  @Nullable
  public static Image loadFromUrl(URL url, boolean dark, boolean retina) {
    return loadFromUrl(url, dark, retina, null);
  }

  @Nullable
  public static Image loadFromUrl(URL url, boolean dark, boolean retina, ImageFilter filter) {
    return ImageDescList.create(url.toString(), null, dark, retina, true).
      load(ImageConverterChain.create().withFilter(filter).withRetina());
  }

  @Nullable
  public static Image loadFromResource(@NonNls @NotNull String s) {
    Class callerClass = ReflectionUtil.getGrandCallerClass();
    if (callerClass == null) return null;
    return loadFromResource(s, callerClass);
  }

  @Nullable
  public static Image loadFromResource(@NonNls @NotNull String path, @NotNull Class aClass) {
    return ImageDescList.create(path, aClass, UIUtil.isUnderDarcula(), UIUtil.isRetina() || JBUI.scale(1.0f) >= 1.5f, true).
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
