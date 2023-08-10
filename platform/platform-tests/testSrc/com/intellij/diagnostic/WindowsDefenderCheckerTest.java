// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import org.junit.Test;

import static com.intellij.openapi.util.io.IoTestUtil.assumeWindows;
import static org.junit.Assert.assertNotNull;

public class WindowsDefenderCheckerTest extends BareTestFixtureTestCase {
  @Test
  public void defenderStatusDetection() {
    assumeWindows();
    assertNotNull(WindowsDefenderChecker.getInstance().isRealTimeProtectionEnabled());
  }
}
