// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public abstract class SVGLoaderCache {
  private static final long MAX_IMAGE_SIZE = 16 * 1024L * 1024L;

  @NotNull
  protected abstract File getCachesHome();

  protected abstract void forkIOTask(@NotNull Runnable action);


  @NotNull
  private File cacheFile(@NotNull byte[] theme, @NotNull byte[] imageBytes, double scale) {
    try {
      MessageDigest d = MessageDigest.getInstance("SHA-256");
      //caches version
      d.update(theme);
      d.update(imageBytes);

      String hex = StringUtil.toHexString(d.digest());
      return new File(getCachesHome(), hex + ".x" + scale);
    }
    catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA1 is not supported!", e);
    }
  }

  @Nullable
  public final BufferedImage loadFromCache(@NotNull byte[] theme,
                                           @NotNull byte[] imageBytes,
                                           double scale,
                                           @NotNull ImageLoader.Dimension2DDouble docSize) {
    File file = cacheFile(theme, imageBytes, scale);
    if (!file.isFile()) return null;

    //let's avoid OOM if an image is too big
    if (file.length() > MAX_IMAGE_SIZE) {
      forkIOTask(() -> FileUtil.delete(file));
      return null;
    }

    try {
      byte[] bytes = FileUtil.loadFileBytes(file);
      ByteBuffer buff = ByteBuffer.wrap(bytes);

      double width = buff.getDouble();
      double height = buff.getDouble();
      int actualWidth = buff.getInt();
      int actualHeight = buff.getInt();

      @SuppressWarnings("UndesirableClassUsage")
      //we do not need a specific image here, it will be wrapped later
      BufferedImage image = new BufferedImage(actualWidth, actualHeight, BufferedImage.TYPE_INT_ARGB);

      for (int y = 0; y < actualHeight; y++) {
        for (int x = 0; x < actualWidth; x++) {
          image.setRGB(x, y, buff.getInt());
        }
      }

      docSize.setSize(width, height);
      return image;
    }
    catch (Exception e) {
      Logger.getInstance(getClass()).warn("Failed to read SVG cache from: " + file + ". " + e.getMessage(), e);
      forkIOTask(() -> FileUtil.delete(file));
      //it is OK if we failed to load an icon
      return null;
    }
  }

  public final void storeLoadedImage(@NotNull byte[] theme,
                                     @NotNull byte[] imageBytes,
                                     double scale,
                                     @NotNull BufferedImage image,
                                     @NotNull ImageLoader.Dimension2DDouble size) {
    if (image.getType() != BufferedImage.TYPE_INT_ARGB) {
      Logger.getInstance(getClass()).warn("Unsupported image type for SVGLoader cache: " + image.getType());
      return;
    }

    forkIOTask(() -> {
      File file = cacheFile(theme, imageBytes, scale);

      int actualWidth = image.getWidth();
      int actualHeight = image.getHeight();

      ByteBuffer buff = ByteBuffer.allocate(actualHeight * actualWidth * Integer.BYTES + 2 * Double.BYTES + 2 * Integer.BYTES);
      buff.putDouble(size.getWidth());
      buff.putDouble(size.getHeight());
      buff.putInt(actualWidth);
      buff.putInt(actualHeight);

      for (int y = 0; y < actualHeight; y++) {
        for (int x = 0; x < actualWidth; x++) {
          buff.putInt(image.getRGB(x, y));
        }
      }

      try {
        final File parentFile = file.getParentFile();
        if (parentFile != null) {
          //noinspection ResultOfMethodCallIgnored
          parentFile.mkdirs();
        }

        FileUtil.writeToFile(file, buff.array());
      } catch (IOException e) {
        Logger.getInstance(getClass()).warn("Failed to write SVG cache to: " + file + ". " + e.getMessage(), e);
      }
    });
  }
}
