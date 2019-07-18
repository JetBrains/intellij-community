// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.IconLoader.CachedImageIcon;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.ui.DeferredIconImpl;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.RestoreScaleRule;
import com.intellij.ui.RetrievableIcon;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.ui.scale.ScaleContextAware;
import com.intellij.ui.scale.UserScaleContext;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.paint.ImageComparator;
import org.jetbrains.annotations.NotNull;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.MalformedURLException;

import static com.intellij.ui.scale.DerivedScaleType.DEV_SCALE;
import static com.intellij.ui.scale.DerivedScaleType.EFF_USR_SCALE;
import static com.intellij.ui.scale.ScaleType.*;
import static com.intellij.util.ui.TestScaleHelper.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

/**
 * Tests that {@link com.intellij.openapi.util.ScalableIcon#scale(float)} works correctly for custom JB icons.
 *
 * @author tav
 */
public class IconScaleTest extends BareTestFixtureTestCase {
  private static final int ICON_BASE_SIZE = 16;
  private static final float ICON_OBJ_SCALE = 1.75f;
  private static final float ICON_OVER_USR_SCALE = 1.0f;

  // 0.75 is impractical system scale factor, however it's used to stress-test the scale subsystem
  private static final double[] SCALES = {0.75f, 1, 2, 2.5f};

  @ClassRule
  public static final ExternalResource manageState = new RestoreScaleRule();

  @Test
  public void testJreHiDpi() throws MalformedURLException {
    assumeTrue(SystemInfo.IS_AT_LEAST_JAVA9 || !SystemInfo.isLinux);

    overrideJreHiDPIEnabled(true);
    try {
      for (double s : SCALES) {
        test(1, s);
      }
    }
    finally {
      overrideJreHiDPIEnabled(false);
    }
  }

  @Test
  public void testIdeHiDpi() throws MalformedURLException {
    for (double s : SCALES) {
      // the system scale repeats the default user scale in IDE-HiDPI
      test(s, s);
    }
  }

  public void test(double usrScale, double sysScale) throws MalformedURLException {
    JBUIScale.setUserScaleFactorForTest((float)usrScale);
    JBUIScale.setSystemScaleFactor((float)sysScale);

    ScaleContext ctx = ScaleContext.create(SYS_SCALE.of(sysScale), USR_SCALE.of(usrScale));

    //
    // 1. CachedImageIcon
    //
    test(new CachedImageIcon(new File(getIconPath()).toURI().toURL()), ctx.copy());

    //
    // 2. DeferredIcon
    //
    CachedImageIcon icon = new CachedImageIcon(new File(getIconPath()).toURI().toURL());
    test(new DeferredIconImpl<>(icon, new Object(), false, o -> icon), UserScaleContext.create(ctx));

    //
    // 3. LayeredIcon
    //
    test(new LayeredIcon(new CachedImageIcon(new File(getIconPath()).toURI().toURL())), UserScaleContext.create(ctx));

    //
    // 4. RowIcon
    //
    test(new com.intellij.ui.RowIcon(new CachedImageIcon(new File(getIconPath()).toURI().toURL())), UserScaleContext.create(ctx));
  }

  private static void test(@NotNull Icon icon, @NotNull UserScaleContext iconContext) {
    ((ScaleContextAware)icon).updateScaleContext(iconContext);

    ScaleContext context = ScaleContext.create(iconContext);

    /*
     * (A) normal conditions
     */

    //noinspection UnnecessaryLocalVariable
    Icon iconA = icon;
    double usrSize2D = context.apply(ICON_BASE_SIZE, EFF_USR_SCALE);
    int usrSize = (int)Math.round(usrSize2D);
    int devSize = (int)Math.round(context.apply(usrSize2D, DEV_SCALE));

    assertIcon(iconA, iconContext, usrSize, devSize);

    /*
     * (B) override scale
     */
    if (!(icon instanceof RetrievableIcon)) { // RetrievableIcon may return a copy of its wrapped icon and we may fail to override scale in the origin.

      Icon iconB = IconUtil.overrideScale(IconUtil.deepCopy(icon, null), USR_SCALE.of(ICON_OVER_USR_SCALE));

      usrSize2D = ICON_BASE_SIZE * ICON_OVER_USR_SCALE * context.getScale(OBJ_SCALE);
      usrSize = (int)Math.round(usrSize2D);
      devSize = (int)Math.round(context.apply(usrSize2D, DEV_SCALE));

      assertIcon(iconB, iconContext, usrSize, devSize);
    }

    /*
     * (C) scale icon
     */
    Icon iconC = IconUtil.scale(icon, null, ICON_OBJ_SCALE);

    assertThat(iconC).isNotSameAs(icon);
    assertThat(((ScaleContextAware)icon).getScaleContext()).isEqualTo(iconContext);

    usrSize2D = context.apply(ICON_BASE_SIZE, EFF_USR_SCALE);
    double scaledUsrSize2D = usrSize2D * ICON_OBJ_SCALE;
    int scaledUsrSize = (int)Math.round(scaledUsrSize2D);
    int scaledDevSize = (int)Math.round(context.apply(scaledUsrSize2D, DEV_SCALE));

    assertIcon(iconC, iconContext, scaledUsrSize, scaledDevSize);

    // Additionally check that the original image hasn't changed after scaling
    Pair<BufferedImage, Graphics2D> pair = createImageAndGraphics(context.getScale(DEV_SCALE), icon.getIconWidth(), icon.getIconHeight());
    BufferedImage iconImage = pair.first;
    Graphics2D g2d = pair.second;

    icon.paintIcon(null, g2d, 0, 0);

    BufferedImage goldImage = loadImage(getIconPath(), context);

    ImageComparator.compareAndAssert(
      new ImageComparator.AASmootherComparator(0.1, 0.1, new Color(0, 0, 0, 0)), goldImage, iconImage, null);
  }

  static void assertIcon(@NotNull Icon icon, @NotNull UserScaleContext iconContext, int usrSize, int devSize) {
    assertThat(icon.getIconWidth()).describedAs("unexpected icon user width (ctx: " + iconContext + ")").isEqualTo(usrSize);
    assertThat(icon.getIconHeight()).describedAs("unexpected icon user height (ctx: " + iconContext + ")").isEqualTo(usrSize);

    ScaleContext context = ScaleContext.create(iconContext);
    assertThat(ImageUtil.getRealWidth(IconUtil.toImage(icon, context))).describedAs("unexpected icon real width (ctx: " + iconContext + ")").isEqualTo(devSize);
    assertThat(ImageUtil.getRealHeight(IconUtil.toImage(icon, context))).describedAs("unexpected icon real height (ctx: " + iconContext + ")").isEqualTo(devSize);
  }

  private static String getIconPath() {
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/abstractClass.svg";
  }
}
