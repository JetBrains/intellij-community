// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.inspections.QuickFixGetFamilyNameViolationInspectionFixture;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.idea.devkit.inspections.QuickFixGetFamilyNameViolationInspectionFixtureKt.quickFixGetFamilyNameViolationInspectionFixture;


/**
 * @author Dmitry Batkovich
 */
@TestDataPath("$CONTENT_ROOT/testData/inspections/getFamilyNameViolation")
@TestApplication
public class KtQuickFixGetFamilyNameViolationInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<QuickFixGetFamilyNameViolationInspectionFixture> familyName =
    quickFixGetFamilyNameViolationInspectionFixture(projectFixture, "kt",
                                                    DevkitKtTestsUtil.TESTDATA_PATH + "inspections/getFamilyNameViolation");

  @Test
  public void testNotViolatedByField() {
    familyName.get().doTest();
  }

  @Test
  public void testNotViolatedByGetName() {
    familyName.get().doTest();
  }

  @Test
  public void testNotViolatedByExternalParameter() {
    familyName.get().doTest();
  }

  @Test
  public void testNotViolatedStaticField() {
    familyName.get().doTest();
  }

  @Test
  public void testNotViolatedStaticMethod() {
    familyName.get().doTest();
  }

  @Test
  public void testNotViolatedGetNameMethod() {
    familyName.get().doTest();
  }

  @Test
  public void testViolationByPsiElementFieldUsage() {
    familyName.get().doTest();
  }

  @Test
  public void testViolationByPsiElementFieldUsageInUsedMethod() {
    familyName.get().doTest();
  }

  @Test
  public void testViolationByPsiElementFieldUsageInUsedParentClassMethod() {
    familyName.get().doTest();
  }
}
