// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class WslDistributionManagerTest extends BareTestFixtureTestCase {

  @Test
  public void caseInsensitiveDistributionName() {
    String ubuntuName = "Ubuntu";
    String lowerCaseUbuntuName = StringUtil.toLowerCase(ubuntuName);
    String debianName = "Debian";
    ServiceContainerUtil.replaceService(
      ApplicationManager.getApplication(), WslDistributionManager.class,
      new WslDistributionManager() {
        @Override
        protected @NotNull List<String> loadInstalledDistributionMsIds() {
          return List.of(ubuntuName, debianName);
        }
      }, getTestRootDisposable());
    WslDistributionManager distributionManager = WslDistributionManager.getInstance();
    WSLDistribution lowerCaseUbuntu = distributionManager.getOrCreateDistributionByMsId(lowerCaseUbuntuName);
    assertEquals(lowerCaseUbuntuName, lowerCaseUbuntu.getMsId());
    WSLDistribution debian = distributionManager.getOrCreateDistributionByMsId(debianName);
    assertEquals(debianName, debian.getMsId());

    // Load the installed distributions to replace previously created distributions with different case.
    distributionManager.getInstalledDistributions();

    assertEquals(ubuntuName, distributionManager.getOrCreateDistributionByMsId(lowerCaseUbuntuName).getMsId());
    assertEquals(debianName, distributionManager.getOrCreateDistributionByMsId(debianName).getMsId());
  }
}
