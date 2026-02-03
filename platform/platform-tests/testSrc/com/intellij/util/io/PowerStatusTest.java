// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.LightPlatform4TestCase;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ArrayUtilRt;
import org.junit.Test;

import java.io.File;

import static com.intellij.util.ObjectUtils.notNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assume.assumeTrue;

public class PowerStatusTest extends LightPlatform4TestCase {
  @Test
  public void getPowerStatusDoesntThrowException() {
    // I.e., it is safe to call it on any OS.
    PowerStatus.getPowerStatus();
  }

  @Test
  public void getPowerStatusReturnsReasonableResult() {
    if (SystemInfo.isLinux) {
      var powerSupplySysNode = "/sys/class/power_supply";
      var sysFsPowerSupplyFiles = notNull(new File(powerSupplySysNode).listFiles(), ArrayUtilRt.EMPTY_FILE_ARRAY);
      assumeTrue("Linux: can't query power source if '" + powerSupplySysNode + "' is empty or not exist", sysFsPowerSupplyFiles.length > 0);
    }

    var status = PowerStatus.getPowerStatus();
    if (UsefulTestCase.IS_UNDER_TEAMCITY) {
      // A UPS could be treated as a battery (see MacPowerService comments: for macOS API, a UPS is a battery).
      // But it is irrelevant for CI agents, and running from a UPS is not a normal thing anyway.
      assertEquals("TeamCity agents are definitely powered from AC line, not battery", PowerStatus.AC, status);
    }
    else {
      assertNotEquals("UNKNOWN status usually means some error in API call", PowerStatus.UNKNOWN, status);
    }
  }
}
