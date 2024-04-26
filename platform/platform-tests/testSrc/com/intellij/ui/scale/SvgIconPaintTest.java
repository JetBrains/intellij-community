// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.scale;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.RestoreScaleRule;
import com.intellij.ui.icons.CachedImageIcon;
import com.intellij.ui.scale.paint.ImageComparator;
import com.intellij.ui.scale.paint.ImageComparator.AASmootherComparator;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.ImageUtil;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Path;

import static com.intellij.ui.scale.ScaleType.SYS_SCALE;
import static com.intellij.ui.scale.TestScaleHelper.loadImage;
import static com.intellij.ui.scale.TestScaleHelper.overrideJreHiDPIEnabled;

/**
 * Tests SVG icon painting.
 *
 * @author tav
 */
public class SvgIconPaintTest {
  static {
    System.setProperty("idea.ui.icons.svg.disk.cache", "false");
  }

  @ClassRule
  public static final ExternalResource manageState = new RestoreScaleRule();

  @BeforeClass
  public static void beforeClass() {
  }

  @Test
  public void test() throws MalformedURLException {
    JBUIScale.setUserScaleFactor((float)2);
    overrideJreHiDPIEnabled(false);

    var icon = new CachedImageIcon(new File(getSvgIconPath()).toURI().toURL(), ScaleContext.create(SYS_SCALE.of(1)));
    BufferedImage iconImage = ImageUtil.toBufferedImage(IconUtil.toImage(icon));

    //saveImage(iconImage, getGoldImagePath().toString()); // uncomment to save gold image

    BufferedImage goldImage = loadImage(getGoldImagePath());

    ImageComparator.compareAndAssert(
      new AASmootherComparator(0.1, 0.1, new Color(0, 0, 0, 0)), iconImage, goldImage, null);
  }

  private static String getSvgIconPath() {
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/abstractClass.svg";
  }

  private static Path getGoldImagePath() {
    return Path.of(PlatformTestUtil.getPlatformTestDataPath() + "ui/gold_abstractClass@2x.png");
  }
}
