// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.scale;

import com.intellij.openapi.util.IconLoader.CachedImageIcon;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.RestoreScaleRule;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.ui.scale.paint.ImageComparator;
import com.intellij.ui.scale.paint.ImageComparator.AASmootherComparator;
import com.intellij.util.ui.JBImageIcon;
import com.intellij.util.ui.StartupUiUtil;
import org.junit.*;
import org.junit.rules.ExternalResource;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.MalformedURLException;

import static com.intellij.ui.scale.ScaleType.SYS_SCALE;
import static com.intellij.ui.scale.TestScaleHelper.*;

/**
 * Tests painting of a slightly scaled icon.
 *
 * @author tav
 */
public class SvgIconScaleAndPaintTest {
  private static final double SYSTEM_SCALE = 2.0;
  private static final float OBJECT_SCALE = 1.2f;

  @ClassRule
  public static final ExternalResource manageState = new RestoreScaleRule();

  @BeforeClass
  public static void beforeClass() {
  }

  @Before
  public void before() {
    setSystemProperty("idea.ui.icons.svg.disk.cache", "false");
  }

  @After
  public void after() {
    restoreProperties();
  }

  @Test
  public void test() throws MalformedURLException {
    JBUIScale.setUserScaleFactor(1f);
    overrideJreHiDPIEnabled(true);

    CachedImageIcon icon = new CachedImageIcon(new File(getSvgIconPath()).toURI().toURL(), false);
    icon.updateScaleContext(ScaleContext.create(SYS_SCALE.of(SYSTEM_SCALE)));

    Icon scaledIcon = icon.scale(OBJECT_SCALE);
    Image scaledImage = IconUtil.toImage(scaledIcon);

    //noinspection UndesirableClassUsage
    BufferedImage paintIconImage = new BufferedImage(ImageUtil.getRealWidth(scaledImage),
                                                     ImageUtil.getRealWidth(scaledImage),
                                                     BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = paintIconImage.createGraphics();
    try {
      g.scale(SYSTEM_SCALE, SYSTEM_SCALE);
      StartupUiUtil.drawImage(g, ((JBImageIcon)scaledIcon).getImage(), 0, 0, null);
    } finally {
      g.dispose();
    }

    //saveImage(paintIconImage, getGoldImagePath()); // uncomment to save gold image

    BufferedImage goldImage = loadImage(getGoldImagePath());

    ImageComparator.compareAndAssert(
      new AASmootherComparator(0.1, 0.1, new Color(0, 0, 0, 0)), paintIconImage, goldImage, null);
  }

  private static String getSvgIconPath() {
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/run.svg";
  }

  private static String getGoldImagePath() {
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/gold_run@2.4x.png";
  }
}
