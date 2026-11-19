// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.idea.devkit.inspections.quickfix.UsePluginIdEqualsFixtureKt.usePluginIdEqualsFixture;

@TestDataPath("$CONTENT_ROOT/testData/inspections/useEqualsFix/pluginId")
@TestApplication
public class UsePluginIdEqualsFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightDevKitInspectionFixture> pluginIdEquals =
    usePluginIdEqualsFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/useEqualsFix/pluginId");

  @Test
  public void testUsePluginIdEquals() {
    pluginIdEquals.get().doTest("Replace '==' with 'equals()'");
  }

  @Test
  public void testUsePluginIdNotEquals() {
    pluginIdEquals.get().doTest("Replace '!=' with '!equals()'");
  }
}
