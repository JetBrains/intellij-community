// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.idea.devkit.inspections.internal.UseDPIAwareInsetsFixtureKt.useDPIAwareInsetsFixture;

@TestDataPath("$CONTENT_ROOT/testData/inspections/useDPIAwareInsets")
@TestApplication
public class UseDPIAwareInsetsInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<UseDPIAwareInsetsFixture> insets =
    useDPIAwareInsetsFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/useDPIAwareInsets");

  @Test
  public void testUseJBUIInsetsThatCanBeSimplified() {
    insets.get().doTest();
  }

  @Test
  public void testUseAwtInsets() {
    insets.get().doTest();
  }
}
