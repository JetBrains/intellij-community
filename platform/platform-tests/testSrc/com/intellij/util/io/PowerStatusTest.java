// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ArrayUtilRt;
import org.junit.Test;

import java.io.File;

import static com.intellij.util.ObjectUtils.notNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assume.assumeTrue;

public class PowerStatusTest {
  @Test
  public void test() {
    assumeTrue(!SystemInfo.isLinux || notNull(new File("/sys/class/power_supply").listFiles(), ArrayUtilRt.EMPTY_FILE_ARRAY).length > 0);

    PowerStatus status = PowerStatus.getPowerStatus();
    if (UsefulTestCase.IS_UNDER_TEAMCITY) {
      assertEquals(PowerStatus.AC, status);
    }
    else {
      assertNotEquals(PowerStatus.UNKNOWN, status);
    }
  }
}