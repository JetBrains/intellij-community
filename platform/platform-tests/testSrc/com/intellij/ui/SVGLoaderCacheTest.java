// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ui.scale.paint.ImageComparator;
import com.intellij.ui.svg.SvgCacheManager;
import com.intellij.ui.svg.SvgCacheMapper;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

public class SVGLoaderCacheTest {
  private static @NotNull SvgCacheManager createCache(@NotNull Path dir) {
    return new SvgCacheManager(dir.resolve("db.db"));
  }

  @Test
  public void testNoEntry(@TempDir Path dir) {
    SvgCacheManager cache = createCache(dir);
    assertThat(cache.loadFromCache(new byte[]{}, new byte[]{}, new SvgCacheMapper(1f))).isNull();
  }

  @Test
  public void testSaveAndLoad(@TempDir Path dir) {
    SvgCacheManager cache = createCache(dir);

    //noinspection UndesirableClassUsage
    BufferedImage i = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
    i.setRGB(0, 0, 0xff00ff);
    i.setRGB(0, 1, 0x00ff00);

    byte[] imageBytes = new byte[]{1, 2, 3};
    byte[] theme = {};
    SvgCacheMapper svgCacheMapper = new SvgCacheMapper(1f);
    cache.storeLoadedImage(theme, imageBytes, svgCacheMapper, i);
    cache.close();
    cache = createCache(dir);

    BufferedImage copy = cache.loadFromCache(theme, imageBytes, svgCacheMapper);

    assertThat(copy.getWidth()).isEqualTo(10);
    assertThat(copy.getHeight()).isEqualTo(10);

    ImageComparator.compareAndAssert(new ImageComparator.AASmootherComparator(0.1, 0.1, new Color(0, 0, 0, 0)), i, copy, null);

    assertThat(cache.loadFromCache(new byte[]{123}, imageBytes, new SvgCacheMapper(1f, false, false))).isNull();
    assertThat(cache.loadFromCache(theme, new byte[]{6, 7}, new SvgCacheMapper(1f, false, false))).isNull();
    assertThat(cache.loadFromCache(theme, imageBytes, new SvgCacheMapper(2f, false, false))).isNull();
  }
}
