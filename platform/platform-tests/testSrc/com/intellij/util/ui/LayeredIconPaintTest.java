// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.IconLoader.CachedImageIcon;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ScalableIcon;
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
import java.util.function.BiFunction;

import static com.intellij.util.ui.JBUI.ScaleType.*;

/**
 * Tests {@link com.intellij.ui.LayeredIcon} painting.
 *
 * @author tav
 */
public class LayeredIconPaintTest extends TestScaleHelper {
  @Test
  public void test() throws MalformedURLException {
    overrideJreHiDPIEnabled(true);

    BiFunction<Integer, Integer, Integer> bit2scale = (mask, bit) -> ((mask >> bit) & 0x1) + 1;

    for (int mask=0; mask<7; mask++) {
      int iconScale = bit2scale.apply(mask, 2);
      int usrScale = bit2scale.apply(mask, 1);
      int sysScale = bit2scale.apply(mask, 0);
      assert iconScale * usrScale * sysScale <= 4;
      test(iconScale, usrScale, sysScale);
    }
  }

  private void test(int iconScale, int usrScale, int sysScale) throws MalformedURLException {
    JBUI.setUserScaleFactor(usrScale);

    CachedImageIcon icon1 = new CachedImageIcon(new File(getIcon1Path()).toURI().toURL());
    CachedImageIcon icon2 = new CachedImageIcon(new File(getIcon2Path()).toURI().toURL());

    ScaleContext ctx = ScaleContext.create(SYS_SCALE.of(sysScale)/*, USR_SCALE.of(usrScale)*/); // USR_SCALE is set automatically
    icon1.updateScaleContext(ctx.copy());
    icon2.updateScaleContext(ctx.copy());

    Icon icon = createAndSetIcons(icon1, icon2).scale(iconScale);
    ctx.update(OBJ_SCALE.of(iconScale));
    test(icon, ctx);
  }

  protected ScalableIcon createAndSetIcons(Icon icon1, Icon icon2) {
    LayeredIcon icon = new LayeredIcon(2);
    icon.setIcon(icon1, 0);
    icon.setIcon(icon2, 1, JBUI.scale(10), JBUI.scale(6));
    return icon;
  }

  private void test(Icon scaledIcon, ScaleContext ctx) {
    Pair<BufferedImage, Graphics2D> pair = createImageAndGraphics(ctx.getScale(SYS_SCALE), scaledIcon.getIconWidth(), scaledIcon.getIconHeight());
    BufferedImage iconImage = pair.first;
    Graphics2D g2d = pair.second;

    scaledIcon.paintIcon(null, g2d, 0, 0);

    //saveImage(iconImage, getGoldImagePath((int)ctx.getScale(PIX_SCALE))); // uncomment to save gold image

    BufferedImage goldImage = loadImage(getGoldImagePath((int)ctx.getScale(PIX_SCALE)));

    ImageComparator.compareAndAssert(
      new AASmootherComparator(0.1, 0.1, new Color(0, 0, 0, 0)), goldImage, iconImage, null);
  }

  protected String getGoldImagePath(int scale) {
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/gold_LayeredIcon@" + scale + "x.png";
  }

  private static String getIcon1Path() {
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/db_set_breakpoint.png";
  }

  private static String getIcon2Path() {
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/question_badge.png";
  }
}
