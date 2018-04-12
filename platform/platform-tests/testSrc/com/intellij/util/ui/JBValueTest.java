// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.ui.RestoreScaleRule;
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
  public void testInt() {
    JBUI.setUserScaleFactor(1);

    JBValue value1 = JBUI.intValue(2);
    JBValue.SelfCachedInteger value2 = new JBValue.SelfCachedInteger(2);

    JBUI.setUserScaleFactor(2);

    assertEquals(JBUI.scale(2), value1.get());
    assertEquals(JBUI.scale(2), value2.get());
  }

  @Test
  public void testFloat() {
    JBUI.setUserScaleFactor(1);

    JBValue.Float value1 = JBUI.floatValue(2.6f);
    JBValue.SelfCachedFloat value2 = new JBValue.SelfCachedFloat(2.6f);

    JBUI.setUserScaleFactor(2);

    assertEquals(Math.round(JBUI.scale(2.6f)), value1.get());
    assertEquals(Math.round(JBUI.scale(2.6f)), value2.get());
    assertEquals(JBUI.scale(2.6f), value1.getFloat());
    assertEquals(JBUI.scale(2.6f), value2.getFloat());
  }

  @Test
  public void testUpdateTracker() {
    JBUI.setUserScaleFactor(1);

    JBValue.UpdateTracker tracker = new JBValue.UpdateTracker();
    JBValue value1 = JBUI.intValue(1, tracker);
    JBValue value2 = JBUI.intValue(2, tracker);
    JBValue.Float value3 = JBUI.floatValue(3.6f, tracker);

    JBUI.setUserScaleFactor(2);

    assertEquals(JBUI.scale(1), value1.get());
    assertEquals(JBUI.scale(2), value2.get());
    assertEquals(Math.round(JBUI.scale(3.6f)), value3.get());
    assertEquals(JBUI.scale(3.6f), value3.getFloat());

    tracker.forget((JBValue.Cacheable)value3);

    int scale = JBUI.scale(1);
    JBUI.setUserScaleFactor(3);

    assertEquals(Math.round(scale * 3.6f), value3.get());
    assertEquals(scale * 3.6f, value3.getFloat());

    tracker.dispose();

    scale = JBUI.scale(1);
    JBUI.setUserScaleFactor(1);

    assertEquals(scale, value1.get());
    assertEquals(scale * 2, value2.get());
  }

  @Test
  public void testUIDefaultsInteger() {
    JBUI.setUserScaleFactor(1);
    String key = "JBValue.int";
    UIManager.put(key, 2);

    JBValue.UIDefaultsInteger value = new JBValue.UIDefaultsInteger(key);

    JBUI.setUserScaleFactor(2);

    assertEquals(JBUI.scale(2), value.get());

    UIManager.put(key, 3);

    assertEquals(JBUI.scale(3), value.get());

    UIManager.put(key, null);
  }
}
