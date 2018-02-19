// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.IconLoader.CachedImageIcon;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBUI.ScaleContext;
import com.intellij.util.ui.paint.AbstractPainter2DTest;
import com.intellij.util.ui.paint.PaintUtilTest;
import junit.framework.TestCase;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.*;
import java.io.File;
import java.net.MalformedURLException;

import static com.intellij.util.ui.JBUI.ScaleType.SYS_SCALE;

/**
 * Tests that {@link CachedImageIcon#scale(float)} doesn't break the contract and scales correctly.
 *
 * @author tav
 */
public class IconScaleTest {

  private static boolean initialSvgProp;

  @Before
  public void setState() {
    RegistryValue rv = Registry.get("ide.svg.icon");
    initialSvgProp = rv.asBoolean();
    rv.setValue(true);

    AbstractPainter2DTest.ScaleState.set();
  }

  @After
  public void restoreState() {
    Registry.get("ide.svg.icon").setValue(initialSvgProp);

    AbstractPainter2DTest.ScaleState.restore();
  }

  @Test
  public void test() throws MalformedURLException {
    Assume.assumeFalse("Linux doesn't support JRE-HiDPI yet", SystemInfo.isLinux);
    test(1.0);
    test(2.0);
    test(2.5);
  }

  public void test(double sysScale) throws MalformedURLException {
    final int BASE_SIZE = 16;
    final float ICON_SCALE = 1.75f;



    PaintUtilTest.overrideJreHiDPIEnabled(true);
    JBUI.setUserScaleFactor(1);

    CachedImageIcon icon = new CachedImageIcon(new File(getIconPath()).toURI().toURL());
    icon.updateScale(SYS_SCALE.of(sysScale));
    ScaleContext originalCtx = icon.getScaleContext().copy();

    TestCase.assertEquals("unexpected icon user width", BASE_SIZE, icon.getIconWidth());
    TestCase.assertEquals("unexpected icon user height", BASE_SIZE, icon.getIconHeight());
    TestCase.assertEquals("unexpected icon real width", 0,
                          Double.compare(BASE_SIZE * sysScale, ImageUtil.getRealWidth(IconUtil.toImage(icon))));
    TestCase.assertEquals("unexpected icon real height", 0,
                          Double.compare(BASE_SIZE * sysScale, ImageUtil.getRealHeight(IconUtil.toImage(icon))));

    Icon scaledIcon = icon.scale(ICON_SCALE);
    TestCase.assertFalse("new instance of the icon is expected", icon == scaledIcon);
    TestCase.assertEquals("ScaleContext of the original icon changed", originalCtx, icon.getScaleContext());

    TestCase.assertEquals("unexpected icon user width", 0,
                          Double.compare(BASE_SIZE * ICON_SCALE, scaledIcon.getIconWidth()));
    TestCase.assertEquals("unexpected icon user height", 0,
                          Double.compare(BASE_SIZE * ICON_SCALE, scaledIcon.getIconHeight()));
    TestCase.assertEquals("unexpected icon real width", 0,
                          Double.compare(BASE_SIZE * ICON_SCALE * sysScale, ImageUtil.getRealWidth(IconUtil.toImage(scaledIcon))));
    TestCase.assertEquals("unexpected icon real height", 0,
                          Double.compare(BASE_SIZE * ICON_SCALE * sysScale, ImageUtil.getRealHeight(IconUtil.toImage(scaledIcon))));
  }

  private String getIconPath() {
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/abstractClass.svg";
  }
}
