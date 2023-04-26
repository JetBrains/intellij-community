// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.scale;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.ui.DeferredIconImpl;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.RestoreScaleExtension;
import com.intellij.ui.RetrievableIcon;
import com.intellij.ui.icons.CachedImageIcon;
import com.intellij.ui.icons.IconUtilKt;
import com.intellij.ui.scale.paint.ImageComparator;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.ImageUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.intellij.ui.scale.DerivedScaleType.DEV_SCALE;
import static com.intellij.ui.scale.DerivedScaleType.EFF_USR_SCALE;
import static com.intellij.ui.scale.ScaleType.*;
import static com.intellij.ui.scale.TestScaleHelper.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests that {@link com.intellij.openapi.util.ScalableIcon#scale(float)} works correctly for custom JB icons.
 * 0.75 is an impractical system scale factor, however, it's used to stress-test the scale subsystem
 *
 * @author tav
 */

public class IconScaleTest extends BareTestFixtureTestCase {
  private static final int ICON_BASE_SIZE = 16;
  private static final float ICON_OBJ_SCALE = 1.75f;
  private static final float ICON_OVER_USR_SCALE = 1.0f;

  @RegisterExtension
  public static final RestoreScaleExtension manageState = new RestoreScaleExtension();

  @ParameterizedTest
  @ValueSource(floats = {0.75f, 1, 2, 2.5f})
  public void jreHiDpi(float scale) throws MalformedURLException {
    assumeTrue(!SystemInfoRt.isLinux);

    overrideJreHiDPIEnabled(true);
    try {
      test(1, scale);
    }
    finally {
      overrideJreHiDPIEnabled(false);
    }
  }

  @ParameterizedTest
  @ValueSource(floats = {0.75f, 1, 2, 2.5f})
  public void ideHiDpi(float scale) throws MalformedURLException {
    // the system scale repeats the default user scale in IDE-HiDPI
    test(scale, scale);
  }

  public void test(float usrScale, float sysScale) throws MalformedURLException {
    JBUIScale.setUserScaleFactorForTest(usrScale);
    JBUIScale.setSystemScaleFactor(sysScale);

    ScaleContext context = ScaleContext.Companion.of(SYS_SCALE.of(sysScale), USR_SCALE.of(usrScale));

    //
    // 1. CachedImageIcon
    //
    test(createIcon(), context.copy());

    //
    // 2. DeferredIcon
    //
    CachedImageIcon icon = createIcon();
    test(new DeferredIconImpl<>(icon, new Object(), false, o -> icon), UserScaleContext.create(context));

    //
    // 3. LayeredIcon
    //
    test(new LayeredIcon(createIcon()), UserScaleContext.create(context));

    //
    // 4. RowIcon
    //
    test(new com.intellij.ui.RowIcon(createIcon()), UserScaleContext.create(context));
  }

  private static @NotNull CachedImageIcon createIcon() throws MalformedURLException {
    return new CachedImageIcon(getIconPath().toUri().toURL(), false);
  }

  private static void test(@NotNull Icon icon, @NotNull UserScaleContext iconUserContext) {
    ((ScaleContextAware)icon).updateScaleContext(iconUserContext);

    ScaleContext iconContext = ScaleContext.Companion.create(iconUserContext);

    /*
     * (A) normal conditions
     */

    //noinspection UnnecessaryLocalVariable
    Icon iconA = icon;
    double usrSize2D = iconContext.apply(ICON_BASE_SIZE, EFF_USR_SCALE);
    int usrSize = (int)Math.round(usrSize2D);
    int devSize = (int)Math.round(iconContext.apply(usrSize2D, DEV_SCALE));

    assertIcon(iconA, iconContext, usrSize, devSize, "Test (A) normal conditions");

    /*
     * (B) override scale
     */
    // RetrievableIcon may return a copy of its wrapped icon,
    // and we may fail to override a scale in the origin.
    if (!(icon instanceof RetrievableIcon)) {
      Icon iconB = IconUtilKt.overrideIconScale(IconUtilKt.copyIcon(icon, null, true), USR_SCALE.of(ICON_OVER_USR_SCALE));

      usrSize2D = ICON_BASE_SIZE * ICON_OVER_USR_SCALE * iconContext.getScale(OBJ_SCALE);
      usrSize = (int)Math.round(usrSize2D);
      devSize = (int)Math.round(iconContext.apply(usrSize2D, DEV_SCALE));

      assertIcon(iconB, iconContext, usrSize, devSize, "Test (B) override scale");
    }

    /*
     * (C) scale icon
     */
    Function<ScaleContext, Pair<Integer /*scaled user size*/, Integer /*scaled dev size*/>> calcScales =
      (ctx) -> {
        double scaledUsrSize2D = ctx.apply(ICON_BASE_SIZE, EFF_USR_SCALE);
        int scaledUsrSize = (int)Math.round(scaledUsrSize2D);
        int scaledDevSize = (int)Math.round(iconContext.apply(scaledUsrSize2D, DEV_SCALE));
        return Pair.create(scaledUsrSize, scaledDevSize);
    };

    Icon iconC = IconUtil.scale(icon, null, ICON_OBJ_SCALE);

    assertThat(iconC).isNotSameAs(icon);
    assertThat(((ScaleContextAware)icon).getScaleContext()).isEqualTo(iconContext);

    ScaleContext contextC = ScaleContext.create(OBJ_SCALE.of(ICON_OBJ_SCALE));
    Pair<Integer, Integer> scales = calcScales.apply(contextC);

    assertIcon(iconC, contextC, scales.first, scales.second, "Test (C) scale icon");

    // Additionally, check that the original image hasn't changed after scaling
    var pair = createImageAndGraphics(iconContext.getScale(DEV_SCALE), icon.getIconWidth(), icon.getIconHeight());
    BufferedImage iconImage = pair.first;
    Graphics2D g2d = pair.second;

    icon.paintIcon(null, g2d, 0, 0);

    BufferedImage goldImage = loadImage(getIconPath(), iconContext);

    ImageComparator.compareAndAssert(
      new ImageComparator.AASmootherComparator(0.1, 0.1, new Color(0, 0, 0, 0)), goldImage, iconImage, null);

    /*
     * (D) scale icon in iconContext
     */
    Consumer<Float> scaleInContext = scale -> {
      ScaleContext contextD = ScaleContext.create(OBJ_SCALE.of(scale));
      Icon iconD = IconUtil.scale(icon, contextD);

      // the new instance is returned
      assertThat(iconD).isNotSameAs(icon);
      // the original icon's iconContext has not changed
      assertThat(((ScaleContextAware)icon).getScaleContext()).isEqualTo(iconContext);

      Pair<Integer, Integer> _scales = calcScales.apply(contextD);
      assertIcon(iconD, contextD, _scales.first, _scales.second, "Test (D) scale icon in iconContext");
    };

    scaleInContext.accept(1f);
    scaleInContext.accept(1.5f);
  }

  private static void assertIcon(@NotNull Icon icon, @NotNull ScaleContext ctx, int usrSize, int devSize, @NotNull String testDescription) {
    assertThat(icon.getIconWidth()).describedAs(testDescription + ": unexpected icon user width").isEqualTo(usrSize);
    assertThat(icon.getIconHeight()).describedAs(testDescription + ": unexpected icon user height").isEqualTo(usrSize);

    assertThat(ImageUtil.getRealWidth(IconLoader.toImage(icon, ctx))).describedAs(testDescription + ": unexpected icon real width")
      .isEqualTo(devSize);
    assertThat(ImageUtil.getRealHeight(IconLoader.toImage(icon, ctx))).describedAs(testDescription + ": unexpected icon real height")
      .isEqualTo(devSize);
  }

  private static Path getIconPath() {
    return Path.of(PlatformTestUtil.getPlatformTestDataPath() + "ui/abstractClass.svg");
  }
}
