// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi;

import com.intellij.openapi.diagnostic.JulLogger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JavaUtilLoggerCleanerTest {
  @Test
  public void testEnsureCleanerDelayed() {
    Assertions.assertTrue(JulLogger.isJulLoggerCleanerDelayed());
  }
}
