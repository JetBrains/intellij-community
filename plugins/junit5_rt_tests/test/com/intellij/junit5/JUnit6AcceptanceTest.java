// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5;

import com.intellij.execution.junit.JUnit6Framework;
import com.intellij.testIntegration.TestFramework;

import java.util.List;

public class JUnit6AcceptanceTest extends JUnitAcceptanceCodeInsightTestBase {
  @Override
  protected List<String> getFrameworkStubs() {
    // JUnit 6 is detected by the presence of MethodOrderer.Default (see JUnit6Framework#getMarkerClassFQNames).
    return List.of("package org.junit.jupiter.api; public interface MethodOrderer { class Default implements MethodOrderer {} }");
  }

  @Override
  protected Class<? extends TestFramework> framework() {
    return JUnit6Framework.class;
  }

  @Override
  protected String runParameter() {
    return "-junit6";
  }

  @Override
  protected String addToClasspathFixText() {
    return "Add 'JUnit6' to classpath";
  }
}
