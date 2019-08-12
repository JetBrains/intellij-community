// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.util.ImageLoader;
import com.intellij.util.SVGLoaderCache;
import com.intellij.util.ui.paint.ImageComparator;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class SVGLoaderCacheTest {
  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  private SVGLoaderCache myCache;

  @Before
  public void setup() throws IOException {
    final File home = temp.newFolder();
    myCache = new SVGLoaderCache() {
      @NotNull
      @Override
      protected File getCachesHome() {
        return home;
      }

      @Override
      protected void forkIOTask(@NotNull Runnable action) {
        action.run();
      }
    };
  }

  @Test
  public void testNoEntry() {
    Assert.assertNull(myCache.loadFromCache("", new byte[]{}, 1.0, new ImageLoader.Dimension2DDouble(0, 0)));
  }

  @Test
  public void testSaveAndLoad() {
    //noinspection UndesirableClassUsage
    BufferedImage i = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
    i.setRGB(0, 0, 0xff00ff);
    i.setRGB(0, 1, 0x00ff00);

    byte[] imageBytes = new byte[]{1, 2, 3};
    myCache.storeLoadedImage("", imageBytes, 1.0, i, new ImageLoader.Dimension2DDouble(20.0, 15.0));

    ImageLoader.Dimension2DDouble copySize = new ImageLoader.Dimension2DDouble(0.0, 0.0);
    BufferedImage copy = myCache.loadFromCache("", imageBytes, 1.0, copySize);

    Assert.assertEquals(20.0, copySize.getWidth(), 0.1);
    Assert.assertEquals(15.0, copySize.getHeight(), 0.1);

    ImageComparator.compareAndAssert(
      new ImageComparator.AASmootherComparator(0.1, 0.1, new Color(0, 0, 0, 0)), i, copy, null);

    final ImageLoader.Dimension2DDouble size = new ImageLoader.Dimension2DDouble(0, 0);
    Assert.assertNull(myCache.loadFromCache("A", imageBytes, 1.0, size));
    Assert.assertNull(myCache.loadFromCache("", new byte[]{6, 7}, 1.0, size));
    Assert.assertNull(myCache.loadFromCache("", imageBytes, 2.0, size));
  }
}
