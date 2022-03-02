// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.TestFixtureRule;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public final class WslDistributionManagerTest {
  @Rule
  public final TestFixtureRule myTestFixtureRule = new TestFixtureRule();

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

        @Override
        public @NotNull List<WslDistributionAndVersion> loadInstalledDistributionsWithVersions() {
          return ContainerUtil.map(loadInstalledDistributionMsIds(), s -> new WslDistributionAndVersion(s, 2));
        }

        @Override
        protected boolean isAvailable() {
          return true;
        }

        @Override
        protected boolean isWslExeSupported() {
          return true;
        }
      }, myTestFixtureRule.getFixture().getTestRootDisposable());
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

  @Test
  public void parseWslVerboseListOutput() {
    var result = WslDistributionManagerImpl.parseWslVerboseListOutput(List.of(
        "  NAME                   STATE           VERSION",
        "* Ubuntu-20.04           Stopped         2",
        "  docker-desktop         Stopped         2",
        "  docker-desktop-data    Stopped         2",
        "  Ubuntu-18.04           Stopped         1"));
    assertEquals(List.of(new WslDistributionAndVersion("Ubuntu-20.04", 2),
                         new WslDistributionAndVersion("Ubuntu-18.04", 1)), result);
  }

  @Test
  public void parseWslVerboseListOutputWithIncorrectDistributionName() {
    try {
      WslDistributionManagerImpl.parseWslVerboseListOutput(List.of(
        "  NAME                   STATE                 VERSION",
        "",
        "* Ubuntu-20.04           Has to be stopped     2",
        "  Ubuntu-18.04           Almost stopped        1 "));
      fail();
    }
    catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("malformed distribution name"));
    }

    try {
      WslDistributionManagerImpl.parseWslVerboseListOutput(List.of(
        "  NAME                   STATE                 VERSION",
        "*"));
      fail();
    }
    catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("malformed distribution name"));
    }
  }

  @Test
  public void parseWslVerboseListOutputWithIncorrectVersion() {
    try {
      WslDistributionManagerImpl.parseWslVerboseListOutput(List.of(
        "  NAME                   STATE                 VERSION",
        "* Ubuntu-20.04           Has to be stopped     second",
        "  Ubuntu-18.04           Almost stopped        1 "));
      fail();
    }
    catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("malformed version"));
    }

    try {
      WslDistributionManagerImpl.parseWslVerboseListOutput(List.of(
        "  NAME                   STATE                 VERSION",
        "* kali-linux",
        "  Ubuntu-18.04           Almost stopped        1 "));
      fail();
    }
    catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("malformed version"));
    }
  }
}
