// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.scale;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.DisableSvgCache;
import com.intellij.ui.RestoreScaleExtension;
import com.intellij.ui.icons.CachedImageIcon;
import com.intellij.ui.icons.CachedImageIconKt;
import com.intellij.ui.scale.paint.ImageComparator;
import com.intellij.ui.scale.paint.ImageComparator.AASmootherComparator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.MalformedURLException;
import java.nio.file.Path;

/**
 * Tests painting of a slightly scaled icon.
 *
 * @author tav
 */
public class SvgIconScaleAndPaintTest {
  private static final double SYSTEM_SCALE = 2.0;
  private static final float OBJECT_SCALE = 1.2f;

  @RegisterExtension
  public static final RestoreScaleExtension manageState = new RestoreScaleExtension();
  @RegisterExtension
  public static final DisableSvgCache disableSvgCache = new DisableSvgCache();

  @Test
  public void test() throws MalformedURLException {
    JBUIScale.setUserScaleFactor(1f);
    TestScaleHelper.overrideJreHiDPIEnabled(true);

    CachedImageIcon icon = CachedImageIconKt.createCachedIcon(Path.of(getSvgIconPath()),
                                                              ScaleContext.create(ScaleType.SYS_SCALE.of(SYSTEM_SCALE)));

    CachedImageIcon scaledIcon = icon.scale(OBJECT_SCALE);
    Image realImage = scaledIcon.getRealImage();

    //noinspection UndesirableClassUsage
    BufferedImage paintIconImage = new BufferedImage(realImage.getWidth(null), realImage.getHeight(null), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = paintIconImage.createGraphics();
    try {
      g.scale(SYSTEM_SCALE, SYSTEM_SCALE);
      scaledIcon.paintIcon(null, g, 0, 0);
    }
    finally {
      g.dispose();
    }

    //saveImage(paintIconImage, getGoldImagePath()); // uncomment to save gold image

    BufferedImage goldImage = TestScaleHelper.loadImage(getGoldImagePath());
    ImageComparator.compareAndAssert(new AASmootherComparator(0.1, 0.1, new Color(0, 0, 0, 0)), paintIconImage, goldImage, null);
  }

  private static String getSvgIconPath() {
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/run.svg";
  }

  private static Path getGoldImagePath() {
    return Path.of(PlatformTestUtil.getPlatformTestDataPath() + "ui/gold_run@2.4x.png");
  }
}
