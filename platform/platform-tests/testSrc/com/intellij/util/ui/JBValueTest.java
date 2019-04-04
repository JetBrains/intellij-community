// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.ui.RestoreScaleRule;
import com.intellij.ui.paint.PaintUtil.RoundingMode;
import com.intellij.util.ui.JBValue.JBValueGroup;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import javax.swing.*;

import static junit.framework.TestCase.assertEquals;

/**
 * Tests {@link JBValue}.
 *
 * @author tav
 */
public class JBValueTest {
  @ClassRule
  public static final ExternalResource manageState = new RestoreScaleRule();

  @Test
  public void testSeparateValue() {
    JBUI.setUserScaleFactor(1);

    JBValue value1 = JBUI.value(2);
    JBValue value2 = JBUI.value(2.6f);
    JBValue value3 = JBUI.value(2.9f);

    JBUI.setUserScaleFactor(2);

    assertEquals(JBUI.scale(2), value1.get());
    assertEquals(Math.round(JBUI.scale(2.6f)), value2.get());
    assertEquals(JBUI.scale(2.6f), value2.getFloat());
    assertEquals((int)Math.ceil(JBUI.scale(2.6f)), value2.get(RoundingMode.CEIL));
    assertEquals((int)Math.floor(JBUI.scale(2.6f)), value3.get(RoundingMode.FLOOR));
  }

  @Test
  public void testGroup() {
    JBUI.setUserScaleFactor(1);

    JBValueGroup group = new JBValueGroup();
    JBValue value1 = group.value(1);
    JBValue value2 = group.value(2);
    JBValue value3 = group.value(3.6f);

    JBUI.setUserScaleFactor(2);

    assertEquals(JBUI.scale(1), value1.get());
    assertEquals(JBUI.scale(2), value2.get());
    assertEquals(Math.round(JBUI.scale(3.6f)), value3.get());
    assertEquals(JBUI.scale(3.6f), value3.getFloat());

    group.dispose();

    int scale = JBUI.scale(1);
    JBUI.setUserScaleFactor(1);

    assertEquals(scale, value1.get());
    assertEquals(scale * 2, value2.get());
    assertEquals(Math.round(scale * 3.6f), value3.get());
    assertEquals(scale * 3.6f, value3.getFloat());
  }

  @Test
  public void testUIInteger() {
    JBUI.setUserScaleFactor(1);
    String key = "JBValue.int";
    String absentKey = "JBValue.absent";
    UIManager.put(key, 2);

    JBValue value1 = JBUI.uiIntValue(key, 1);
    JBValue value2 = JBUI.uiIntValue(absentKey, 1);

    JBUI.setUserScaleFactor(2);

    assertEquals(JBUI.scale(2), value1.get());
    assertEquals(JBUI.scale(1), value2.get());

    UIManager.put(key, 3);

    assertEquals(JBUI.scale(3), value1.get());

    UIManager.put(key, null);
  }
}
