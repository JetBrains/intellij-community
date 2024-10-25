// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.scale;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.ui.DeferredIconImpl;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.RestoreScaleExtension;
import com.intellij.ui.icons.CachedImageIcon;
import com.intellij.ui.icons.TextIcon;
import com.intellij.ui.scale.paint.ImageComparator;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.ImageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
import java.util.function.Supplier;

import static com.intellij.ui.icons.CustomIconUtilKt.loadIconCustomVersionOrScale;
import static com.intellij.ui.icons.CustomIconUtilKt.scaleIconOrLoadCustomVersion;
import static com.intellij.ui.scale.DerivedScaleType.DEV_SCALE;
import static com.intellij.ui.scale.DerivedScaleType.EFF_USR_SCALE;
import static com.intellij.ui.scale.ScaleType.*;
import static com.intellij.ui.scale.TestScaleHelper.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests that {@link com.intellij.openapi.util.ScalableIcon#scale(float)} works correctly for custom JB icons.
 * 0.75 is an impractical system scale factor; however, it's used to stress-test iconContext.apply(usrSize2D, DEV_SCALE)the scale subsystem
 *
 * @author tav
 */
public class IconScaleTest extends BareTestFixtureTestCase {
  private static final int ICON_BASE_SIZE = 16;
  private static final float ICON_OBJ_SCALE = 1.75f;

  @RegisterExtension
  public static final RestoreScaleExtension manageState = new RestoreScaleExtension();

  @AfterEach
  void tearDown() {
    // we don't want subsequent tests to be affected by whatever the previous tests set
    JBUIScale.setUserScaleFactorForTest(1.0f);
    JBUIScale.setSystemScaleFactor(1.0f);
  }

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

    ScaleContext context = ScaleContext.Companion.of(new Scale[]{SYS_SCALE.of(sysScale), USR_SCALE.of(usrScale)});

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
    test(LayeredIcon.layeredIcon(new Icon[]{createIcon()}), UserScaleContext.create(context));

    //
    // 4. RowIcon
    //
    test(new com.intellij.ui.RowIcon(createIcon()), UserScaleContext.create(context));
  }

  private static @NotNull CachedImageIcon createIcon() {
    Path path = getIconPath();
    try {
      return new CachedImageIcon(path.toUri().toURL(), null);
    }
    catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private static void test(@NotNull Icon icon, @NotNull UserScaleContext iconUserContext) {
    //((ScaleContextAware)icon).updateScaleContext(iconUserContext);

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
     * (C) scale icon
     */
    Function<ScaleContext, Pair<Integer /*scaled user size*/, Integer /*scaled dev size*/>> calcScales = (ctx) -> {
      double scaledUsrSize2D = ctx.apply(ICON_BASE_SIZE, EFF_USR_SCALE);
      int scaledUsrSize = (int)Math.round(scaledUsrSize2D);
      int scaledDevSize = (int)Math.round(iconContext.apply(scaledUsrSize2D, DEV_SCALE));
      return new Pair<>(scaledUsrSize, scaledDevSize);
    };

    Icon iconC = IconUtil.scale(icon, null, ICON_OBJ_SCALE);

    assertThat(iconC).isNotSameAs(icon);
    //assertThat(((ScaleContextAware)icon).getScaleContext()).isEqualTo(iconContext);

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
      //assertThat(((ScaleContextAware)icon).getScaleContext()).isEqualTo(iconContext);

      Pair<Integer, Integer> _scales = calcScales.apply(contextD);
      assertIcon(iconD, contextD, _scales.first, _scales.second, "Test (D) scale icon in iconContext");
    };

    scaleInContext.accept(1f);
    scaleInContext.accept(1.5f);
  }

  private static void assertIcon(@NotNull Icon icon, @Nullable ScaleContext ctx, int usrSize, int devSize, @NotNull String testDescription) {
    assertThat(icon.getIconWidth()).describedAs(testDescription + ": unexpected icon user width").isEqualTo(usrSize);
    assertThat(icon.getIconHeight()).describedAs(testDescription + ": unexpected icon user height").isEqualTo(usrSize);

    Image image = IconLoader.toImage(icon, ctx);
    assertThat(ImageUtil.getRealWidth(image)).describedAs(testDescription + ": unexpected icon real width").isEqualTo(devSize);
    assertThat(ImageUtil.getRealHeight(image)).describedAs(testDescription + ": unexpected icon real height").isEqualTo(devSize);
  }


  private static Path getIconPath() {
    return Path.of(PlatformTestUtil.getPlatformTestDataPath() + "ui/abstractClass.svg");
  }

  @Test
  void scaleIconOrLoadCustomVersionOnCachedImageIcon() {
    scaleIconOrLoadCustomVersionTest(() -> createIcon());
  }

  @Test
  void scaleIconOrLoadCustomVersionOnNonCachedImageIcon() {
    scaleIconOrLoadCustomVersionTest(() -> new TextIcon("test", new JLabel(), 12.0f));
  }

  private static void scaleIconOrLoadCustomVersionTest(Supplier<ScalableIcon> sample) {
    var quadIcon = sample.get().scale(4.0f);
    // check that scaleIconOrLoadCustomVersion() has the same effect as scale()
    var scaledIcon = (ScalableIcon)scaleIconOrLoadCustomVersion(sample.get(), 4.0f);
    assertThat(scaledIcon.getScale()).isCloseTo(4.0f, offset(0.01f));
    compareIcons(scaledIcon, quadIcon);
    // check that scaleIconOrLoadCustomVersion() scales relative to the original size
    var scaledTwiceIcon = scaleIconOrLoadCustomVersion(scaleIconOrLoadCustomVersion(sample.get(), 2.0f), 4.0f);
    assertThat(scaledIcon.getScale()).isCloseTo(4.0f, offset(0.01f));
    compareIcons(scaledTwiceIcon, quadIcon);
  }

  @Test
  void loadIconCustomVersionOrScaleOnCachedImageIcon() {
    loadIconCustomVersionOrScaleTest(() -> createIcon());
  }

  @Test
  void loadIconCustomVersionOrScaleOnNonCachedImageIcon() {
    loadIconCustomVersionOrScaleTest(() -> new TextIcon("test", new JLabel(), 12.0f));
  }

  private static void loadIconCustomVersionOrScaleTest(Supplier<ScalableIcon> sample) {
    var size = sample.get().getIconWidth();
    var quadIcon = sample.get().scale(4.0f);
    // check that loadIconCustomVersionOrScale() has the same effect as scale()
    var scaledIcon = (ScalableIcon)loadIconCustomVersionOrScale(sample.get(), size * 4);
    assertThat(scaledIcon.getScale()).isCloseTo(4.0f, offset(0.01f));
    compareIcons(scaledIcon, quadIcon);
    // check that loadIconCustomVersionOrScale() takes the previous scaling factor into account
    var doubleIcon = (ScalableIcon)loadIconCustomVersionOrScale(sample.get(), size * 2);
    assertThat(doubleIcon.getScale()).isCloseTo(2.0f, offset(0.01f));
    // We can't just do size * 4 here because the previous scaling isn't guaranteed to be exact.
    var scaledTwiceIcon = (ScalableIcon)loadIconCustomVersionOrScale(doubleIcon, doubleIcon.getIconWidth() * 2);
    assertThat(scaledTwiceIcon.getScale()).isCloseTo(4.0f, offset(0.01f));
    compareIcons(scaledTwiceIcon, quadIcon);
  }

  private static void compareIcons(Icon actual, Icon expected) {
    var actualImage = renderIcon(actual);
    var expectedImage = renderIcon(expected);
    ImageComparator.compareAndAssert(
      new ImageComparator.AASmootherComparator(0.1, 0.1, new Color(0, 0, 0, 0)), expectedImage, actualImage, null);
  }

  private static BufferedImage renderIcon(Icon icon) {
    var pair = createImageAndGraphics(1.0, icon.getIconWidth(), icon.getIconHeight());
    BufferedImage iconImage = pair.first;
    Graphics2D g2d = pair.second;
    icon.paintIcon(null, g2d, 0, 0);
    return iconImage;
  }
}
