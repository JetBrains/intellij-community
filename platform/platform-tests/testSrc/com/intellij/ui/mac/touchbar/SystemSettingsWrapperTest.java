// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import junit.framework.TestCase;
import org.junit.Assume;
import org.junit.Test;

public class SystemSettingsWrapperTest extends TestCase {
  private static final String testAppID = "com.apple.terminal";

  @Test
  public void testGetProcessOutput() {
    Assume.assumeTrue(NST.isSupportedOS());

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
  public void testSettingsWrite() {
    Assume.assumeTrue(NST.isSupportedOS());

    final SystemSettingsWrapper settings = new SystemSettingsWrapper(testAppID);
    Assume.assumeTrue(settings.isSettingsDomainExists());

    final boolean enabled = settings.isShowFnKeysEnabled();
    settings.setShowFnKeysEnabled(!enabled, false);
    assertEquals(settings.isShowFnKeysEnabled(), !enabled);

    settings.setShowFnKeysEnabled(enabled, false);
    assertEquals(settings.isShowFnKeysEnabled(), enabled);
  }
}
