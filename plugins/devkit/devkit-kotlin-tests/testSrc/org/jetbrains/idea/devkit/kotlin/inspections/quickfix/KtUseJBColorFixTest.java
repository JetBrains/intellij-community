// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.quickfix;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.quickfix.UseJBColorFixTestBase;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/useJBColorFix")
public class KtUseJBColorFixTest extends UseJBColorFixTestBase {

  @Override
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/useJBColorFix";
  }

  @Override
  protected @NotNull String getFileExtension() {
    return "kt";
  }

  public void testUseJBColorConstantInConstant() {
    doTest(CONVERT_TO_JB_COLOR_CONSTANT_FIX_NAME);
  }

  public void testUseJBColorConstantInMethodParam() {
    doTest(CONVERT_TO_JB_COLOR_CONSTANT_FIX_NAME);
  }

  public void testUseJBColorConstantInVariable() {
    doTest(CONVERT_TO_JB_COLOR_CONSTANT_FIX_NAME);
  }

  public void testUseJBColorConstantStaticImportInVariable() {
    doTest(CONVERT_TO_JB_COLOR_CONSTANT_FIX_NAME);
  }
}
