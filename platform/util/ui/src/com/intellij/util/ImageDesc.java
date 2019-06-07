// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.icons.IconLoadMeasurer;
import com.intellij.ui.icons.ImageType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

public final class ImageDesc {
  private static final ConcurrentMap<String, Pair<Image, ImageLoader.Dimension2DDouble>> ourCache = ContainerUtil.createConcurrentSoftValueMap();

  final @NotNull String path;
  final double scale; // initial scale factor
  final @NotNull ImageType type;
  final boolean original; // path is not altered
  // The original user space size of the image. In case of SVG it's the size specified in the SVG doc.
  // Otherwise it's the size of the original image divided by the image's scale (defined by the extension @2x).
  final @NotNull ImageLoader.Dimension2DDouble origUsrSize;

  private static final IconLoadMeasurer loadingSvgStats = new IconLoadMeasurer(ImageType.SVG);
  private static final IconLoadMeasurer loadingPngStats = new IconLoadMeasurer(ImageType.IMG);

  public interface LoadTimeConsumer {
    void accept(@NotNull ImageType type, int value);
  }

  @Nullable
  static volatile LoadTimeConsumer loadTimeConsumer;

  ImageDesc(@NotNull String path, double scale, @NotNull ImageType type) {
    this(path, scale, type, false);
  }

  ImageDesc(double scale) {
    this("", scale, ImageType.IMG, false);
  }

  public static void setLoadTimeConsumer(@Nullable LoadTimeConsumer loadTimeConsumer) {
    ImageDesc.loadTimeConsumer = loadTimeConsumer;
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance("#com.intellij.util.ImageLoader");
  }

  public static void clearCache() {
    ourCache.clear();
  }

  ImageDesc(@NotNull String path, double scale, @NotNull ImageType type, boolean original) {
    this.path = path;
    this.scale = scale;
    this.type = type;
    this.original = original;
    this.origUsrSize = new ImageLoader.Dimension2DDouble(0, 0);
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
        cacheKey = path + (type == ImageType.SVG ? "_@" + scale + "x" : "");
        Pair<Image, ImageLoader.Dimension2DDouble> pair = ourCache.get(cacheKey);
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
    if (image != null && cacheKey != null && 4L * image.getWidth(null) * image.getHeight(null) <= ImageLoader.CACHED_IMAGE_MAX_SIZE) {
      ourCache.put(cacheKey, Pair.create(image, origUsrSize));
    }
    return image;
  }

  @Nullable
  private Image loadImpl(@Nullable URL url, @NotNull InputStream stream) throws IOException {
    LoadTimeConsumer loadTimeConsumer = ImageDesc.loadTimeConsumer;
    long start = StartUpMeasurer.isEnabled() || loadTimeConsumer != null ? StartUpMeasurer.getCurrentTime() : -1;
    IconLoadMeasurer stats;
    Image image;
    switch (type) {
      case SVG: {
        stats = loadingSvgStats;
        image = SVGLoader.load(url, stream, scale, origUsrSize);
      }
      break;

      case IMG: {
        stats = loadingPngStats;
        image = loadImpl(stream);
      }
      break;

      default:
        return null;
    }

    if (start != -1) {
      int duration = (int)(StartUpMeasurer.getCurrentTime() - start);
      stats.add(duration);
      if (loadTimeConsumer != null) {
        loadTimeConsumer.accept(type, duration);
      }
    }
    return image;
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

  @NotNull
  public static List<IconLoadMeasurer> getStats() {
    return Arrays.asList(loadingSvgStats, loadingPngStats);
  }

  @Override
  public String toString() {
    return path + ", scale: " + scale + ", type: " + type;
  }

  @SuppressWarnings("UnusedReturnValue")
  private static boolean waitForImage(Image image) {
    if (image == null) return false;
    if (image.getWidth(null) > 0) return true;
    MediaTracker mediatracker = new MediaTracker(ImageLoader.ourComponent);
    mediatracker.addImage(image, 1);
    try {
      mediatracker.waitForID(1, 5000);
    }
    catch (InterruptedException ex) {
      getLogger().info(ex);
    }
    return !mediatracker.isErrorID(1);
  }
}
