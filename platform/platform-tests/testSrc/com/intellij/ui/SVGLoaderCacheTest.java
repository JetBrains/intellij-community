// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.testFramework.rules.InMemoryFsRule;
import com.intellij.ui.scale.paint.ImageComparator;
import com.intellij.ui.svg.SvgCacheManager;
import com.intellij.util.ImageLoader;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.awt.*;
import java.awt.image.BufferedImage;

public class SVGLoaderCacheTest {
  @Rule
  public final InMemoryFsRule fsRule = new InMemoryFsRule();

  private SvgCacheManager cache;

  @Before
  public void setup() {
    cache = new SvgCacheManager(fsRule.getFs().getPath("/db"));
  }

  @Test
  public void testNoEntry() {
    Assert.assertNull(cache.loadFromCache(new byte[]{}, new byte[]{}, 1f, false, new ImageLoader.Dimension2DDouble(0, 0)));
  }

  @Test
  public void testSaveAndLoad() {
    //noinspection UndesirableClassUsage
    BufferedImage i = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
    i.setRGB(0, 0, 0xff00ff);
    i.setRGB(0, 1, 0x00ff00);

    byte[] imageBytes = new byte[]{1, 2, 3};
    byte[] theme = {};
    cache.storeLoadedImage(theme, imageBytes, 1f, i);

    ImageLoader.Dimension2DDouble copySize = new ImageLoader.Dimension2DDouble(0.0, 0.0);
    Image copy = cache.loadFromCache(theme, imageBytes, 1f, false, copySize);

    Assert.assertEquals(10.0, copySize.getWidth(), 0.1);
    Assert.assertEquals(10.0, copySize.getHeight(), 0.1);

    ImageComparator.compareAndAssert(new ImageComparator.AASmootherComparator(0.1, 0.1, new Color(0, 0, 0, 0)), i, copy, null);

    ImageLoader.Dimension2DDouble size = new ImageLoader.Dimension2DDouble(0, 0);
    Assert.assertNull(cache.loadFromCache(new byte[]{123}, imageBytes, 1f, false, size));
    Assert.assertNull(cache.loadFromCache(theme, new byte[]{6, 7}, 1f, false, size));
    Assert.assertNull(cache.loadFromCache(theme, imageBytes, 2f, false, size));
  }
}
