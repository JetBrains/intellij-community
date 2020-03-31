// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class OverlyComplexBooleanExpressionInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/controlflow/overly_complex_boolean_expression";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  private void doTest() {
    OverlyComplexBooleanExpressionInspection tool = new OverlyComplexBooleanExpressionInspection();
    tool.m_limit = 3;
    tool.m_ignorePureConjunctionsDisjunctions = true;
    myFixture.enableInspections(tool);
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testOverlyComplexBooleanExpression() {
    doTest();
  }

}
