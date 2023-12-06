// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application;

import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import org.junit.Test;

public class ApplicationFlagsBareTest extends BareTestFixtureTestCase implements StressDetectable{
  @Test
  public void testStressFlagSetCorrectly() {
    assertStressTestDetected(true);
  }
  @Test
  public void testPerformanceFlagSetCorrectly() {
    assertStressTestDetected(true);
  }
  @Test
  public void testSlowFlagSetCorrectly() {
    assertStressTestDetected(true);
  }
  @Test
  public void testAppFlagsSetCorrectly() {
    assertStressTestDetected(false);
  }
}
