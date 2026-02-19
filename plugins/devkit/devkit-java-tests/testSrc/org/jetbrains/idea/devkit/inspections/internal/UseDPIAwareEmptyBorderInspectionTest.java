// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/useDPIAwareEmptyBorder")
public class UseDPIAwareEmptyBorderInspectionTest extends UseDPIAwareEmptyBorderInspectionTestBase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/useDPIAwareEmptyBorder";
  }

  @Override
  protected @NotNull String getFileExtension() {
    return "java";
  }

  public void testUseJBUIBordersEmptyThatCanBeSimplified() {
    doTest();
  }

  public void testUseSwingEmptyBorder() {
    doTest();
  }
}
