// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.icons.IconLoadMeasurer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public abstract class SVGLoaderCache {
  private static final long MAX_IMAGE_SIZE = 16 * 1024L * 1024L;

  @NotNull
  protected abstract Path getCachesHome();

  protected abstract void forkIOTask(@NotNull Runnable action);


  @NotNull
  private Path cacheFile(@NotNull byte[] theme, @NotNull byte[] imageBytes, double scale) {
    try {
      MessageDigest d = MessageDigest.getInstance("SHA-256");
      //caches version
      d.update(theme);
      d.update(imageBytes);

      String hex = StringUtil.toHexString(d.digest());
      return getCachesHome().resolve(hex + ".x" + scale);
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
    Path file = cacheFile(theme, imageBytes, scale);
    if (!Files.isRegularFile(file)) {
      return null;
    }

    //let's avoid OOM if an image is too big
    try {
      long size = Files.size(file);
      if (size > MAX_IMAGE_SIZE) {
        forkIOTask(() -> {
          try {
            FileUtil.delete(file);
          }
          catch (IOException ignore) {
          }
        });
        return null;
      }
    }
    catch (IOException e) {
      return null;
    }

    try {
      long start = StartUpMeasurer.isEnabled() ? StartUpMeasurer.getCurrentTime() : -1;

      byte[] bytes = Files.readAllBytes(file);
      BufferedImage image = SVGLoaderCacheIO.readImageFile(bytes, docSize);
      IconLoadMeasurer.svgCacheRead.addDurationStartedAt(start);

      return image;
    }
    catch (Exception e) {
      Logger.getInstance(getClass()).warn("Failed to read SVG cache from: " + file + ". " + e.getMessage(), e);
      forkIOTask(() -> {
        try {
          FileUtil.delete(file);
        }
        catch (IOException ignore) {
        }
      });
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
      long start = StartUpMeasurer.isEnabled() ? StartUpMeasurer.getCurrentTime() : -1;

      Path file = cacheFile(theme, imageBytes, scale);
      try {
        SVGLoaderCacheIO.writeImageFile(file, image, size);
      }
      catch (Exception e) {
        Logger.getInstance(SVGLoaderCache.class).warn("Failed to write SVG cache to: " + file + ". " + e.getMessage(), e);
      }

      IconLoadMeasurer.svgCacheWrite.addDurationStartedAt(start);
    });
  }
}
