// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/useCoupleFix")
public class UseCoupleFixTest extends UseCoupleFixTestBase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/useCoupleFix";
  }

  @Override
  protected @NotNull String getFileExtension() {
    return "java";
  }

  // factory methods tests:

  public void testUseCoupleOfFactoryMethodInConstantWhenPairCreateUsed() {
    doTest(CONVERT_TO_COUPLE_OF_FIX_NAME);
  }

  public void testUseCoupleOfFactoryMethodInVariableWhenPairCreateUsed() {
    doTest(CONVERT_TO_COUPLE_OF_FIX_NAME);
  }

  public void testUseCoupleOfFactoryMethodInMethodParameterWhenPairCreateUsed() {
    doTest(CONVERT_TO_COUPLE_OF_FIX_NAME);
  }

  public void testUseCoupleOfFactoryMethodInConstantWhenPairPairUsed() {
    doTest(CONVERT_TO_COUPLE_OF_FIX_NAME);
  }

  public void testUseCoupleOfFactoryMethodInVariableWhenPairPairUsed() {
    doTest(CONVERT_TO_COUPLE_OF_FIX_NAME);
  }

  public void testUseCoupleOfFactoryMethodInMethodParameterWhenPairPairUsed() {
    doTest(CONVERT_TO_COUPLE_OF_FIX_NAME);
  }

  // type tests:

  public void testUseCoupleTypeInConstant() {
    doTest(CONVERT_TO_COUPLE_TYPE_FIX_NAME);
  }

  public void testUseCoupleTypeInVariable() {
    doTest(CONVERT_TO_COUPLE_TYPE_FIX_NAME);
  }

  public void testUseCoupleTypeInMethodParameter() {
    doTest(CONVERT_TO_COUPLE_TYPE_FIX_NAME);
  }
}
