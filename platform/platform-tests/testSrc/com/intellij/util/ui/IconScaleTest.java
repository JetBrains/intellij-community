// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.IconLoader.CachedImageIcon;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.ui.DeferredIconImpl;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.RestoreScaleRule;
import com.intellij.ui.RowIcon;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBUI.BaseScaleContext;
import com.intellij.util.ui.JBUI.ScaleContext;
import com.intellij.util.ui.JBUI.ScaleContextAware;
import com.intellij.util.ui.paint.ImageComparator;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.MalformedURLException;

import static com.intellij.util.ui.JBUI.ScaleType.SYS_SCALE;
import static com.intellij.util.ui.JBUI.ScaleType.USR_SCALE;
import static com.intellij.util.ui.TestScaleHelper.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

/**
 * Tests that {@link com.intellij.openapi.util.ScalableIcon#scale(float)} works correctly for custom JB icons.
 *
 * @author tav
 */
public class IconScaleTest extends BareTestFixtureTestCase {
  private static final int ICON_BASE_SIZE = 16;
  private static final float ICON_OBJ_SCALE = 1.75f;

  @ClassRule
  public static final ExternalResource manageState = new RestoreScaleRule();

  @BeforeClass
  public static void beforeClass() {
    setRegistryProperty("ide.svg.icon", "true");
  }

  @Test
  public void test() throws MalformedURLException {
    final double[] SCALES = {1, 2, 2.5};

    //
    // 1) JRE-HiDPI
    //
    overrideJreHiDPIEnabled(true);
    if (!SystemInfo.isLinux) { // Linux doesn't support JRE-HiDPI yet
      for (double s : SCALES) test(1, s);
    }

    //
    // 2) IDE-HiDPI
    //
    overrideJreHiDPIEnabled(false);
    for (double s : SCALES) test(s, 1);
  }

  public void test(double usrScale, double sysScale) throws MalformedURLException {
    JBUI.setUserScaleFactor((float)usrScale);
    ScaleContext ctx = ScaleContext.create(SYS_SCALE.of(sysScale), USR_SCALE.of(usrScale));

    //
    // 1. CachedImageIcon
    //
    test(new CachedImageIcon(new File(getIconPath()).toURI().toURL()), ctx.copy());

    //
    // 2. DeferredIcon
    //
    CachedImageIcon icon = new CachedImageIcon(new File(getIconPath()).toURI().toURL());
    test(new DeferredIconImpl<>(icon, new Object(), false, o -> icon), BaseScaleContext.create(ctx));

    //
    // 3. LayeredIcon
    //
    test(new LayeredIcon(new CachedImageIcon(new File(getIconPath()).toURI().toURL())), BaseScaleContext.create(ctx));

    //
    // 4. RowIcon
    //
    test(new RowIcon(new CachedImageIcon(new File(getIconPath()).toURI().toURL())), BaseScaleContext.create(ctx));
  }

  private static void test(Icon icon, BaseScaleContext ctx) {
    ((ScaleContextAware)icon).updateScaleContext(ctx);

    int usrSize = (int)Math.round(ICON_BASE_SIZE * ctx.getScale(USR_SCALE));
    int devSize = (int)Math.round(usrSize * ctx.getScale(SYS_SCALE));

    assertEquals("unexpected icon user width", usrSize, icon.getIconWidth());
    assertEquals("unexpected icon user height", usrSize, icon.getIconHeight());
    assertEquals("unexpected icon real width", devSize, ImageUtil.getRealWidth(IconUtil.toImage(icon)));
    assertEquals("unexpected icon real height", devSize, ImageUtil.getRealHeight(IconUtil.toImage(icon)));

    Icon scaledIcon = IconUtil.scale(icon, null, ICON_OBJ_SCALE);

    assertNotSame("scaled instance of the icon", icon, scaledIcon);
    assertEquals("ScaleContext of the original icon changed", ctx, ((ScaleContextAware)icon).getScaleContext());

    int scaledUsrSize = Math.round(usrSize * ICON_OBJ_SCALE);
    int scaledDevSize = Math.round(devSize * ICON_OBJ_SCALE);

    assertEquals("unexpected scaled icon user width", scaledUsrSize, scaledIcon.getIconWidth());
    assertEquals("unexpected scaled icon user height", scaledUsrSize, scaledIcon.getIconHeight());
    assertEquals("unexpected scaled icon real width", scaledDevSize, ImageUtil.getRealWidth(IconUtil.toImage(scaledIcon)));
    assertEquals("unexpected scaled icon real height", scaledDevSize, ImageUtil.getRealHeight(IconUtil.toImage(scaledIcon)));

    // Additionally check that the original image hasn't changed after scaling
    Pair<BufferedImage, Graphics2D> pair = createImageAndGraphics(ctx.getScale(SYS_SCALE), icon.getIconWidth(), icon.getIconHeight());
    BufferedImage iconImage = pair.first;
    Graphics2D g2d = pair.second;

    icon.paintIcon(null, g2d, 0, 0);

    BufferedImage goldImage = loadImage(getIconPath(), ScaleContext.create(ctx));

    ImageComparator.compareAndAssert(
      new ImageComparator.AASmootherComparator(0.1, 0.1, new Color(0, 0, 0, 0)), goldImage, iconImage, null);
  }

  private static String getIconPath() {
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/abstractClass.svg";
  }
}
