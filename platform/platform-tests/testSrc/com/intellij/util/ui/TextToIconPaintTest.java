// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.ScalableIcon;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.RestoreScaleRule;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBUI.ScaleContext;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import javax.swing.*;

import static com.intellij.util.ui.JBUI.ScaleType.*;

public class TextToIconPaintTest extends CompositeIconPaintTestHelper {
  @ClassRule
  public static final ExternalResource manageState = new RestoreScaleRule();

  @Test
  @Override
  public void test() {
    super.test();
  }

  @Override
  protected ScalableIcon createCompositeIcon(Icon... cellIcons) {
    return (ScalableIcon)IconUtil.textToIcon("IDEA", new JPanel(), JBUI.scale(12));
  }

  @Override
  protected String getGoldImagePath(ScaleContext ctx) {
    int usrScale = (int)(ctx.getScale(USR_SCALE) * ctx.getScale(OBJ_SCALE));
    int sysScale = (int)(ctx.getScale(SYS_SCALE));
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/gold_TextIcon@" + usrScale + "x" + sysScale + "x.png";
  }

  @Override
  protected boolean shouldSaveGoldImage() {
    return false;
  }

  @Override
  protected String[] getCellIconsPaths() {
    return new String[] {}; // just pretends to be composite
  }
}
