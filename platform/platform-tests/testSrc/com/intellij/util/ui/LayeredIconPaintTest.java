// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.IconLoader.CachedImageIcon;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.ui.JBUI.ScaleContext;
import com.intellij.util.ui.paint.ImageComparator;
import com.intellij.util.ui.paint.ImageComparator.AASmootherComparator;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.MalformedURLException;

import static com.intellij.util.ui.JBUI.ScaleType.PIX_SCALE;
import static com.intellij.util.ui.JBUI.ScaleType.SYS_SCALE;
import static com.intellij.util.ui.JBUI.ScaleType.USR_SCALE;

/**
 * Tests {@link com.intellij.ui.LayeredIcon} painting.
 *
 * @author tav
 */
public class LayeredIconPaintTest extends TestScaleHelper {
  @Test
  public void test() throws MalformedURLException {
    JBUI.setUserScaleFactor(1);
    overrideJreHiDPIEnabled(true);

    test(1, 1);
    test(1, 2);
    test(2, 1);
    test(2, 2);
  }

  public void test(int usrScale, int sysScale) throws MalformedURLException {
    LayeredIcon icon = new LayeredIcon(2);
    CachedImageIcon icon1 = new CachedImageIcon(new File(getIcon1Path()).toURI().toURL());
    CachedImageIcon icon2 = new CachedImageIcon(new File(getIcon2Path()).toURI().toURL());

    ScaleContext ctx = ScaleContext.create(USR_SCALE.of(usrScale), SYS_SCALE.of(sysScale));
    icon1.updateScaleContext(ctx.copy());
    icon2.updateScaleContext(ctx.copy());

    icon.setIcon(icon1, 0);
    icon.setIcon(icon2, 1, 10, 6);

    Icon scaledIcon = icon.scale(usrScale);

    Pair<BufferedImage, Graphics2D> pair = createImageAndGraphics(sysScale, scaledIcon.getIconWidth(), scaledIcon.getIconHeight());
    BufferedImage iconImage = pair.first;
    Graphics2D g2d = pair.second;

    scaledIcon.paintIcon(null, g2d, 0, 0);

    //saveImage(iconImage, getGoldImagePath((int)ctx.getScale(PIX_SCALE))); // uncomment to save gold image

    BufferedImage goldImage = loadImage(getGoldImagePath((int)ctx.getScale(PIX_SCALE)));

    ImageComparator.compareAndAssert(
      new AASmootherComparator(0.1, 0.1, new Color(0, 0, 0, 0)), goldImage, iconImage, null);
  }

  private static String getGoldImagePath(int scale) {
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/gold_LayeredIcon@" + scale + "x.png";
  }

  private static String getIcon1Path() {
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/db_set_breakpoint.png";
  }

  private static String getIcon2Path() {
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/question_badge.png";
  }
}
