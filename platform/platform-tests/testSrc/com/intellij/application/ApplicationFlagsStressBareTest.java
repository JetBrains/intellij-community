// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application;

import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import org.junit.Test;

public class ApplicationFlagsStressBareTest extends BareTestFixtureTestCase implements StressDetectable {
  @Test
  public void testStrssFlagSetCorrectly() {
    assertStressTestDetected(true);
  }
  @Test
  public void testPerfFlagSetCorrectly() {
    assertStressTestDetected(true);
  }
  @Test
  public void testSloFlagSetCorrectly() {
    assertStressTestDetected(true);
  }
  @Test
  public void testAppFlagsSetCorrectly() {
    assertStressTestDetected(true);
  }
}
