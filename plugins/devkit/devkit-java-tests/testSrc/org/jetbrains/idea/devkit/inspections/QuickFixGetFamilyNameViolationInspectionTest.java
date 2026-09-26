// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

/**
 * @author Dmitry Batkovich
 */
@TestDataPath("$CONTENT_ROOT/testData/inspections/getFamilyNameViolation")
public class QuickFixGetFamilyNameViolationInspectionTest extends QuickFixGetFamilyNameViolationInspectionTestBase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/getFamilyNameViolation";
  }

  @Override
  protected @NotNull String getFileExtension() {
    return "java";
  }

  public void testNotViolatedByField() {
    doTest();
  }

  public void testNotViolatedByGetName() {
    doTest();
  }

  public void testNotViolatedByExternalParameter() {
    doTest();
  }

  public void testNotViolatedStaticField() {
    doTest();
  }

  public void testNotViolatedStaticMethod() {
    doTest();
  }

  public void testNotViolatedGetNameMethod() {
    doTest();
  }

  public void testViolationByPsiElementFieldUsage() {
    doTest();
  }

  public void testViolationByPsiElementFieldUsageInUsedMethod() {
    doTest();
  }

  public void testViolationByPsiElementFieldUsageInUsedParentClassMethod() {
    doTest();
  }
}
