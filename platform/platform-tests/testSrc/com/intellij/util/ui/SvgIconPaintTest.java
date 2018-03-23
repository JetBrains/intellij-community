// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.IconLoader.CachedImageIcon;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.ImageLoader;
import com.intellij.util.ui.JBUI.ScaleContext;
import com.intellij.util.ui.paint.ImageComparator;
import org.junit.Before;
import org.junit.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.MalformedURLException;

import static com.intellij.util.ui.JBUI.ScaleType.SYS_SCALE;
import static junit.framework.TestCase.assertTrue;

/**
 * Tests SVG icon painting.
 *
 * @author tav
 */
public class SvgIconPaintTest extends TestScaleHelper {
  @Before
  @Override
  public void setState() {
    super.setState();
    setRegistryProperty("ide.svg.icon", "true");
  }

  @Test
  public void test() throws MalformedURLException {
    JBUI.setUserScaleFactor(2);
    overrideJreHiDPIEnabled(false);

    CachedImageIcon icon = new CachedImageIcon(new File(getSvgIconPath()).toURI().toURL());
    icon.updateScaleContext(ScaleContext.create(SYS_SCALE.of(1)));
    BufferedImage iconImage = ImageUtil.toBufferedImage(IconUtil.toImage(icon));
    //save(iconImage);
    BufferedImage goldImage = load();

    ImageComparator comparator = new ImageComparator(new ImageComparator.ColorAASmoother(0, 0.3f));
    StringBuilder sb = new StringBuilder("images mismatch: ");
    assertTrue(sb.toString(), comparator.compare(iconImage, goldImage, sb));
  }

  @SuppressWarnings("unused")
  private static void save(BufferedImage bi) {
    try {
      javax.imageio.ImageIO.write(bi, "png", new File(getGoldImagePath()));
    } catch (java.io.IOException e) {
      e.printStackTrace();
    }
  }

  private static BufferedImage load() {
    try {
      Image img = ImageLoader.loadFromUrl(
        new File(getGoldImagePath()).toURI().toURL(), false, false, null, ScaleContext.createIdentity());
      return ImageUtil.toBufferedImage(img);
    }
    catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private static String getSvgIconPath() {
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/abstractClass.svg";
  }

  private static String getGoldImagePath() {
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/gold_abstractClass@2x.png";
  }
}
