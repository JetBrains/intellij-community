// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.ui.RestoreScaleRule;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import static com.intellij.util.ui.TestScaleHelper.overrideJreHiDPIEnabled;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

/**
 * Tests {@link EmptyIcon} scaling behaviour.
 *
 * @author tav
 */
public class EmptyIconScaleTest {
  @ClassRule
  public static final ExternalResource manageState = new RestoreScaleRule();


  final static String MSG = "the icon size mismatch";
  final static int SIZE = 16;

  @Test
  public void test() throws ClassNotFoundException {
    // force static init for EmptyIcon to set JBUI.scale listener
    assertNotNull(Class.forName(EmptyIcon.class.getName()));

    // EmptyIcon is JRE-HiDPI unaware
    overrideJreHiDPIEnabled(false);

    test(2);

    // change the user scale and make sure EmptyIcon.cache doesn't hold incorrect icons
    test(3);
  }

  public void test(float scale) {
    JBUI.setUserScaleFactor(scale);

    // 1) create unscaled
    EmptyIcon icon = EmptyIcon.create(SIZE);

    assertEquals(MSG, SIZE, icon.getIconWidth());

    // 2) created scaled
    icon = EmptyIcon.create(JBUI.scale(SIZE));

    assertEquals(MSG, JBUI.scale(SIZE), icon.getIconWidth());

    // 3) create unscaled and then scale
    icon = JBUI.scale(EmptyIcon.create(SIZE));

    assertEquals(MSG, JBUI.scale(SIZE), icon.getIconWidth());

    // 4) create unscaled again
    icon = EmptyIcon.create(SIZE);

    assertEquals(MSG, SIZE, icon.getIconWidth());
  }
}
