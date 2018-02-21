// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.IconLoader.CachedImageIcon;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBUI.ScaleContext;
import junit.framework.TestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.swing.*;
import java.io.File;
import java.net.MalformedURLException;

import static com.intellij.util.ui.JBUI.ScaleType.SYS_SCALE;
import static com.intellij.util.ui.JBUI.ScaleType.USR_SCALE;

/**
 * Tests that {@link CachedImageIcon#scale(float)} doesn't break the contract and scales correctly.
 *
 * @author tav
 */
public class IconScaleTest extends TestScaleHelper {

  private static boolean initialSvgProp;

  @Before
  @Override
  public void setState() {
    super.setState();

    RegistryValue rv = Registry.get("ide.svg.icon");
    initialSvgProp = rv.asBoolean();
    rv.setValue(true);
  }

  @After
  @Override
  public void restoreState() {
    super.restoreState();

    Registry.get("ide.svg.icon").setValue(initialSvgProp);
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

    final int ICON_BASE_SIZE = 16;
    final int ICON_USER_SIZE = (int)Math.round(ICON_BASE_SIZE * ctx.getScale(USR_SCALE));
    final int ICON_REAL_SIZE = (int)Math.round(ICON_USER_SIZE * ctx.getScale(SYS_SCALE));

    final float ICON_SCALE = 1.75f;
    final int ICON_SCALED_USER_SIZE = Math.round(ICON_USER_SIZE * ICON_SCALE);
    final int ICON_SCALED_REAL_SIZE = Math.round(ICON_REAL_SIZE * ICON_SCALE);

    CachedImageIcon icon = new CachedImageIcon(new File(getIconPath()).toURI().toURL());
    icon.updateScaleContext(ctx);

    TestCase.assertEquals("unexpected icon user width", ICON_USER_SIZE, icon.getIconWidth());
    TestCase.assertEquals("unexpected icon user height", ICON_USER_SIZE, icon.getIconHeight());
    TestCase.assertEquals("unexpected icon real width", ICON_REAL_SIZE, ImageUtil.getRealWidth(IconUtil.toImage(icon)));
    TestCase.assertEquals("unexpected icon real height", ICON_REAL_SIZE, ImageUtil.getRealHeight(IconUtil.toImage(icon)));

    Icon scaledIcon = icon.scale(ICON_SCALE);
    TestCase.assertNotSame("new instance of the icon is expected", icon, scaledIcon);
    TestCase.assertEquals("ScaleContext of the original icon changed", ctx, icon.getScaleContext());

    TestCase.assertEquals("unexpected scaled icon user width", ICON_SCALED_USER_SIZE, scaledIcon.getIconWidth());
    TestCase.assertEquals("unexpected scaled icon user height", ICON_SCALED_USER_SIZE, scaledIcon.getIconHeight());
    TestCase.assertEquals("unexpected scaled icon real width", ICON_SCALED_REAL_SIZE, ImageUtil.getRealWidth(IconUtil.toImage(scaledIcon)));
    TestCase.assertEquals("unexpected scaled icon real height", ICON_SCALED_REAL_SIZE, ImageUtil.getRealHeight(IconUtil.toImage(scaledIcon)));
  }

  private String getIconPath() {
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/abstractClass.svg";
  }
}
