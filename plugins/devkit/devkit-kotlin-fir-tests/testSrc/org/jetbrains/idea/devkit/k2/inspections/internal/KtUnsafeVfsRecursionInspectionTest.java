// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections.internal;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixture;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.idea.devkit.inspections.internal.UnsafeVfsRecursionInspectionFixtureKt.unsafeVfsRecursionInspectionFixture;

@TestDataPath("$CONTENT_ROOT/testData/inspections/unsafeVfsRecursion")
@TestApplication
public class KtUnsafeVfsRecursionInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightDevKitInspectionFixture> vfsRecursion =
    unsafeVfsRecursionInspectionFixture(projectFixture, "kt", DevkitKtTestsUtil.TESTDATA_PATH + "inspections/unsafeVfsRecursion");

  @Test
  public void testUnsafeVfsRecursion() {
    vfsRecursion.get().doTest();
  }

  @Test
  public void testUnsafeVfsRecursionCorrectCases() {
    vfsRecursion.get().doTest();
  }
}
