// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.svg;

import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.icons.IconLoadMeasurer;
import com.intellij.util.ImageLoader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ikv.Ikv;

import java.awt.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
public final class SvgPrebuiltCacheManager {
  private final Path dbDir;
  private final Map<Float, Ikv> scaleToMap = new ConcurrentHashMap<>(2, 0.75f, 2);

  public SvgPrebuiltCacheManager(@NotNull Path dbDir) throws IOException {
    this.dbDir = dbDir;
  }

  public @Nullable Image loadFromCache(int key, float scale, boolean isDark, @NotNull ImageLoader.Dimension2DDouble docSize) {
    // not supported scale
    if (scale != 1f && scale != 1.25f && scale != 1.5f && scale != 2.0f && scale != 2.5f) {
      return null;
    }

    long start = StartUpMeasurer.getCurrentTimeIfEnabled();
    try {
      @SuppressWarnings("resource") ByteBuffer data = scaleToMap.computeIfAbsent(scale + (isDark ? 10_000 : 0), __ -> {
        String fileName = "icons-v1-" + scale + (isDark ? "-d" : "") + ".db";
        try {
          return Ikv.loadIkv(dbDir.resolve(fileName));
        }
        catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }).getValue(key);
      if (data == null) {
        return null;
      }

      int actualWidth;
      int actualHeight;

      int format = data.get() & 0xFF;
      if (format < 254) {
        actualWidth = format;
        //noinspection SuspiciousNameCombination
        actualHeight = actualWidth;
      }
      else if (format == 255) {
        actualWidth = readVar(data);
        //noinspection SuspiciousNameCombination
        actualHeight = actualWidth;
      }
      else {
        actualWidth = readVar(data);
        actualHeight = readVar(data);
      }

      float width = actualWidth / scale;
      float height = actualHeight / scale;

      docSize.setSize(width, height);

      Image image = SvgCacheManager.readImage(data, actualWidth, actualHeight);
      IconLoadMeasurer.svgPreBuiltLoad.end(start);
      return image;
    }
    catch (UncheckedIOException e) {
      Logger.getInstance(SvgPrebuiltCacheManager.class).error(e.getCause());
      return null;
    }
    catch (Throwable e) {
      Logger.getInstance(SvgPrebuiltCacheManager.class).error(e);
      return null;
    }
  }

  private static int readVar(ByteBuffer buf) {
    byte aByte = buf.get();
    int value = aByte & 127;
    if ((aByte & 128) != 0) {
      aByte = buf.get();
      value |= (aByte & 127) << 7;
      if ((aByte & 128) != 0) {
        aByte = buf.get();
        value |= (aByte & 127) << 14;
        if ((aByte & 128) != 0) {
          aByte = buf.get();
          value |= (aByte & 127) << 21;
          if ((aByte & 128) != 0) {
            value |= buf.get() << 28;
          }
        }
      }
    }
    return value;
  }
}
