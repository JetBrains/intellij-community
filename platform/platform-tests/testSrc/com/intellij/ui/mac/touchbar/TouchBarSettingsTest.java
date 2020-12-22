// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.ui.mac.foundation.NSDefaults;
import junit.framework.TestCase;
import org.junit.Assume;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class TouchBarSettingsTest extends TestCase {
  private static final String testAppID = "com.apple.terminal";

  @Test
  public void testGetProcessOutput() {
    Assume.assumeTrue("NST-unsupported OS", NST.isSupportedOS());

    final GeneralCommandLine cmdLine = new GeneralCommandLine("pgrep", "ser");
    try {
      final ProcessOutput out = ExecUtil.execAndGetOutput(cmdLine);
      assertNotNull("ProcessOutput mustn't be null", out);
      assertNotNull("ProcessOutput.getStdout() mustn't be null", out.getStdout());
    } catch (ExecutionException e) {
      fail("pgrep failed with exception: " + e.getMessage());
    }

    // TODO:
    // 1. ensure that isTouchbarServerRunning() == true on some known models
    // for example MacBookPro14,3 (it's the model indentifier, don't mix with screen size)
    // 2. test that can restart server (i.e. pkill with sudo)
    // 3. check that 'isTouchbarServerRunning() == isSettingsDomainExists()'
  }

  @Test
  public void testSettingsRead() {
    IoTestUtil.assumeMacOS();

    final String sysVer = NSDefaults.readStringVal("loginwindow", "SystemVersionStampAsString");
    assertNotNull(sysVer);
    assertFalse(sysVer.isEmpty());
  }

  @Test
  public void testTouchBarSettingsWrite() {
    Assume.assumeTrue("NST-unsupported OS", NST.isSupportedOS());

    if (NSDefaults.isDomainExists(NSDefaults.ourTouchBarDomain)) {
      NSDefaults.removePersistentDomain(NSDefaults.ourTouchBarDomain);
      Assume.assumeTrue("can't delete domain: " + NSDefaults.ourTouchBarDomain, !NSDefaults.isDomainExists(NSDefaults.ourTouchBarDomain));
    }

    final Map<String, Object> vals = new HashMap<>();
    vals.put("TestNSDefaultsKey", "TestNSDefaultsValue");
    vals.put("PresentationModePerApp", new HashMap<>());
    NSDefaults.createPersistentDomain(NSDefaults.ourTouchBarDomain, vals);

    Assume.assumeTrue("can't create domain: " + NSDefaults.ourTouchBarDomain, NSDefaults.isDomainExists(NSDefaults.ourTouchBarDomain));

    final boolean enabled = NSDefaults.isShowFnKeysEnabled(testAppID);
    NSDefaults.setShowFnKeysEnabled(testAppID, !enabled);
    assertEquals(NSDefaults.isShowFnKeysEnabled(testAppID), !enabled);

    NSDefaults.setShowFnKeysEnabled(testAppID, enabled);
    assertEquals(NSDefaults.isShowFnKeysEnabled(testAppID), enabled);
  }
}
