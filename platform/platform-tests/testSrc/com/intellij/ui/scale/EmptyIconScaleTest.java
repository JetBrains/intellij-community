// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.scale;

import com.intellij.ui.RestoreScaleExtension;
import com.intellij.util.ui.EmptyIcon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.intellij.ui.scale.TestScaleHelper.overrideJreHiDPIEnabled;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests {@link EmptyIcon} scaling behaviour.
 *
 * @author tav
 */
public class EmptyIconScaleTest {
  @RegisterExtension
  public static final RestoreScaleExtension manageState = new RestoreScaleExtension();

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
    JBUIScale.setUserScaleFactor(scale);

    // 1) create unscaled
    EmptyIcon icon = EmptyIcon.create(SIZE);

    assertEquals(SIZE, icon.getIconWidth(), MSG);

    // 2) created scaled
    icon = EmptyIcon.create(JBUIScale.scale(SIZE));

    assertEquals(JBUIScale.scale(SIZE), icon.getIconWidth(), MSG);

    // 3) create unscaled and then scale
    icon = JBUIScale.scaleIcon(EmptyIcon.create(SIZE));

    assertEquals(JBUIScale.scale(SIZE), icon.getIconWidth(), MSG);

    // 4) create unscaled again
    icon = EmptyIcon.create(SIZE);

    assertEquals(SIZE, icon.getIconWidth(), MSG);
  }
}
