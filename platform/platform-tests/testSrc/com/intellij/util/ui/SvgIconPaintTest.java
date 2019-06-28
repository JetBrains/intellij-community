// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.IconLoader.CachedImageIcon;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.RestoreScaleRule;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.paint.ImageComparator;
import com.intellij.util.ui.paint.ImageComparator.AASmootherComparator;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.MalformedURLException;

import static com.intellij.ui.scale.ScaleType.SYS_SCALE;
import static com.intellij.util.ui.TestScaleHelper.loadImage;
import static com.intellij.util.ui.TestScaleHelper.overrideJreHiDPIEnabled;

/**
 * Tests SVG icon painting.
 *
 * @author tav
 */
public class SvgIconPaintTest {
  @ClassRule
  public static final ExternalResource manageState = new RestoreScaleRule();

  @BeforeClass
  public static void beforeClass() {
  }

  @Test
  public void test() throws MalformedURLException {
    JBUIScale.setUserScaleFactor((float)2);
    overrideJreHiDPIEnabled(false);

    CachedImageIcon icon = new CachedImageIcon(new File(getSvgIconPath()).toURI().toURL());
    icon.updateScaleContext(ScaleContext.create(SYS_SCALE.of(1)));
    BufferedImage iconImage = ImageUtil.toBufferedImage(IconUtil.toImage(icon));

    //saveImage(iconImage, getGoldImagePath()); // uncomment to save gold image

    BufferedImage goldImage = loadImage(getGoldImagePath());

    ImageComparator.compareAndAssert(
      new AASmootherComparator(0.1, 0.1, new Color(0, 0, 0, 0)), iconImage, goldImage, null);
  }

  private static String getSvgIconPath() {
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/abstractClass.svg";
  }

  private static String getGoldImagePath() {
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/gold_abstractClass@2x.png";
  }
}
