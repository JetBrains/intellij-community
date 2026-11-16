// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections.internal;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.inspections.internal.UseJBColorFixture;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.idea.devkit.inspections.internal.UseJBColorFixtureKt.useJBColorFixture;

@TestDataPath("$CONTENT_ROOT/testData/inspections/useJBColor")
@TestApplication
public class KtUseJBColorInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<UseJBColorFixture> jbColor =
    useJBColorFixture(projectFixture, "kt", DevkitKtTestsUtil.TESTDATA_PATH + "inspections/useJBColor");

  @Test
  public void testUseJBColorConstructor() {
    jbColor.get().doTest();
  }

  @Test
  public void testUseJBColorConstant() {
    jbColor.get().doTest();
  }
}
