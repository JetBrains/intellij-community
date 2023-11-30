// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    //i.e. it is safe to call it on any OS:
    PowerStatus.getPowerStatus();
  }

  @Test
  public void getPowerStatusReturnsReasonableResult() {
    if (SystemInfo.isLinux) {
      String powerSupplySysNode = "/sys/class/power_supply";
      File[] sysFsPowerSupplyFiles = notNull(new File(powerSupplySysNode).listFiles(), ArrayUtilRt.EMPTY_FILE_ARRAY);
      assumeTrue("Linux: can't query power source if '" + powerSupplySysNode + "' is empty or not exist",
                 sysFsPowerSupplyFiles.length > 0);
    }


    PowerStatus status = PowerStatus.getPowerStatus();
    if (UsefulTestCase.IS_UNDER_TEAMCITY) {
      //UPS could be treated as battery (see MacPowerService comments: for Mac API UPS is 'battery').
      // But I doubt this is actual for our agents, and running from UPS is not a normal thing anyway.
      assertEquals(
        "TeamCity agents are definitely powered from AC line, not battery",
        PowerStatus.AC,
        status
      );
    }
    else {
      assertNotEquals(
        "UNKNOWN status usually means some error in API call",
        PowerStatus.UNKNOWN,
        status
      );
    }
  }
}