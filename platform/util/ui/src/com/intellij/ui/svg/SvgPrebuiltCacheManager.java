// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.svg;

import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ui.icons.IconLoadMeasurer;
import com.intellij.util.ImageLoader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.mvstore.MVMap;
import org.jetbrains.mvstore.MVStore;
import org.jetbrains.mvstore.type.IntDataType;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
public final class SvgPrebuiltCacheManager {
  private final MVStore store;
  private final Map<Float, MVMap<Integer, ImageValue>> scaleToMap = new ConcurrentHashMap<>(2, 0.75f, 2);
  private final MVMap.Builder<Integer, ImageValue> mapBuilder;

  public SvgPrebuiltCacheManager(@NotNull Path dbFile) throws IOException {
    MVStore.Builder storeBuilder = new MVStore.Builder()
      .readOnly()
      .backgroundExceptionHandler((e, store) -> {
        SvgCacheManager.getLogger().error("Icon cache error (db=" + store.getFileStore() + ")", e);
      });
    store = storeBuilder.open(dbFile);

    MVMap.Builder<Integer, ImageValue> mapBuilder = new MVMap.Builder<>();
    mapBuilder.keyType(IntDataType.INSTANCE);
    mapBuilder.valueType(new ImageValue.ImageValueSerializer());
    this.mapBuilder = mapBuilder;
  }

  public @Nullable Image loadFromCache(int key,
                                       float scale,
                                       boolean isDark,
                                       @NotNull ImageLoader.Dimension2DDouble docSize) {
    long start = StartUpMeasurer.getCurrentTimeIfEnabled();
    try {
      ImageValue data = SvgCacheManager.getMap(scale, isDark, scaleToMap, store, mapBuilder).get(key);
      if (data == null) {
        return null;
      }

      Image image = SvgCacheManager.readImage(data, docSize);
      IconLoadMeasurer.svgPreBuiltLoad.end(start);
      return image;
    }
    catch (Exception e) {
      SvgCacheManager.getLogger().error(e);
      return null;
    }
  }
}
