// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.idea.devkit.k2.inspections.InstanceIElementTypeFieldFixtureKt.instanceIElementTypeFieldFixture;

@TestApplication
public class InstanceIElementTypeFieldInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightDevKitInspectionFixture> devKit = instanceIElementTypeFieldFixture(projectFixture, "java");

  @Test
  public void testInstanceFieldWithIElementType() {
    devKit.get().doTest();
  }

  @Test
  public void testStaticFieldNoWarning() {
    devKit.get().doTest();
  }

  @Test
  public void testSubtypeDetection() {
    devKit.get().doTest();
  }

  @Test
  public void testQuickFix() {
    devKit.get().doTest("Make field 'static final'");
  }

  @Test
  public void testEnumFieldsNoWarning() {
    devKit.get().doTest();
  }
}
