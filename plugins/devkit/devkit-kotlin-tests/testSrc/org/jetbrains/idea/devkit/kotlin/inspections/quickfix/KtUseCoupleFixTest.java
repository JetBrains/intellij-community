// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.quickfix;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.quickfix.UseCoupleFixTestBase;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/useCoupleFix")
public class KtUseCoupleFixTest extends UseCoupleFixTestBase {

  @Override
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/useCoupleFix";
  }

  @Override
  protected @NotNull String getFileExtension() {
    return "kt";
  }

  public void testUseCoupleOfFactoryMethodInConstantWhenPairCreateUsed() {
    doTest(CONVERT_TO_COUPLE_OF_FIX_NAME);
  }

  public void testUseCoupleOfFactoryMethodInVariableWhenPairCreateUsed() {
    doTest(CONVERT_TO_COUPLE_OF_FIX_NAME);
  }

  public void testUseCoupleOfFactoryMethodInMethodParameterWhenPairCreateUsed() {
    doTest(CONVERT_TO_COUPLE_OF_FIX_NAME);
  }

  public void testUseCoupleOfFactoryMethodInMethodParameterWhenPairCreateStaticImportUsed() {
    doTest(CONVERT_TO_COUPLE_OF_FIX_NAME);
  }

  public void testUseCoupleOfFactoryMethodInConstantWhenPairPairUsed() {
    doTest(CONVERT_TO_COUPLE_OF_FIX_NAME);
  }
}
