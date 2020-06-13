// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.controlflow;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.ConditionalExpressionInspection;

public class ConditionalExpressionFixTest extends IGQuickFixesTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ConditionalExpressionInspection());
    myRelativePath = "controlflow/conditional_expression";
    myDefaultHint = InspectionGadgetsBundle.message("conditional.expression.quickfix");
  }

  public void testThisCall() { assertQuickfixNotAvailable(); }
  public void testBrokenCode() { assertQuickfixNotAvailable(); }
  public void testField() { doTest(); }
  public void testNonDenotableVar() { assertQuickfixNotAvailable(); }

  public void testArrayInitializer() { doTest(); }
  public void testExpandVar() { doTest(); }
  public void testCastNeeded() { doTest(); }
  public void testComment() { doTest(); }
  public void testCommentWithDeclaration() { doTest(); }
  public void testConditionalAsArgument() { doTest(); }
  public void testConditionalInBinaryExpression() { doTest(); }
  public void testConditionalInIf() { doTest(); }
  public void testConditionalInIfBranch() { doTest(); }
  public void testConditionalInIfBranch2() { doTest(); }
  public void testInsideExprLambda() { doTest(); }
  public void testInsideExprLambdaWithParams() { doTest(); }
  public void testParentheses() { doTest(); }
  public void testNestedConditional() { doTest(); }
  public void testInsideSwitchExpression() { doTest(); }
  public void testInsideThrow() {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    CommonCodeStyleSettings javaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
    doTest();
  }
  public void testNestedConditionalOuter() { doTest(); }

  public void testSimpleOption() {
    final ConditionalExpressionInspection inspection = new ConditionalExpressionInspection();
    inspection.ignoreSimpleAssignmentsAndReturns = true;
    myFixture.enableInspections(inspection);
    doTest();
  }
  
  public void testSwitchExpressionInside() { doTest(); }
  public void testNullType() { doTest(); }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder builder) {
    builder.setLanguageLevel(LanguageLevel.JDK_14);
  }
}