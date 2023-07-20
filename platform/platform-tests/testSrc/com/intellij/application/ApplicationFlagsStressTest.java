// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application;

import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testFramework.UsefulTestCase;

/**
 * test that {@link ApplicationManagerEx#isInStressTest()} correctly return true for tests with "stress"/etc string in their names.
 * test that {@link Logger} doesn't have debug level enabled for stress tests
 */
public class ApplicationFlagsStressTest extends UsefulTestCase implements StressDetectable {
  public void testStrsFlagSetCorrectly() {
    assertStressTestDetected(true);
  }
  public void testPerfFlagSetCorrectly() {
    assertStressTestDetected(true);
  }
  public void testSloFlagSetCorrectly() {
    assertStressTestDetected(true);
  }
  public void testAppFlagsSetCorrectly() {
    assertStressTestDetected(true);
  }
}
