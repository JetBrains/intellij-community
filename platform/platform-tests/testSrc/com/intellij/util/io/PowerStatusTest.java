// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.testFramework.UsefulTestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class PowerStatusTest {
  @Test
  public void test() {
    PowerStatus status = PowerStatus.getPowerStatus();
    if (UsefulTestCase.IS_UNDER_TEAMCITY) {
      assertEquals(PowerStatus.AC, status);
    }
    else {
      assertNotEquals(PowerStatus.UNKNOWN, status);
    }
  }
}