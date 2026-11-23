// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.idea.devkit.inspections.quickfix.UseVirtualFileEqualsFixtureKt.useVirtualFileEqualsFixture;

@TestDataPath("$CONTENT_ROOT/testData/inspections/useEqualsFix/virtualFile")
@TestApplication
public class UseVirtualFileEqualsFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightDevKitInspectionFixture> virtualFileEquals =
    useVirtualFileEqualsFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/useEqualsFix/virtualFile");

  @Test
  public void testUseVirtualFileEquals() {
    virtualFileEquals.get().doTest("Replace '==' with 'equals()'");
  }

  @Test
  public void testUseVirtualFileNotEquals() {
    virtualFileEquals.get().doTest("Replace '!=' with '!equals()'");
  }
}
