// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.quickfix;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.quickfix.ConvertToGrayQuickFixTestBase;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/useGrayFix")
public class KtConvertToGrayQuickFixTest extends ConvertToGrayQuickFixTestBase {

  @Override
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/useGrayFix";
  }

  @Override
  protected @NotNull String getFileExtension() {
    return "kt";
  }

  public void testUseGrayConstantFixInConstant() {
    doTest(CONVERT_TO_GRAY_FIX_NAME_PATTERN.formatted(125));
  }

  public void testUseGrayConstantFixInLocalVariable() {
    doTest(CONVERT_TO_GRAY_FIX_NAME_PATTERN.formatted(25));
  }

  public void testUseGrayConstantFixInMethodParam() {
    doTest(CONVERT_TO_GRAY_FIX_NAME_PATTERN.formatted(125));
  }

  public void testUseGrayConstantFixInJBColorParam() {
    doTest(CONVERT_TO_GRAY_FIX_NAME_PATTERN.formatted(25));
  }

  public void testUseGrayConstantFixWhenNumberConstantsReferenced() {
    doTest(CONVERT_TO_GRAY_FIX_NAME_PATTERN.formatted(125));
  }
}
