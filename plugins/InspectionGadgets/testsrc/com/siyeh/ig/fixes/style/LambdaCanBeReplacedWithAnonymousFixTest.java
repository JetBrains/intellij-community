/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.fixes.style;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.LambdaCanBeReplacedWithAnonymousInspection;

public class LambdaCanBeReplacedWithAnonymousFixTest extends IGQuickFixesTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new LambdaCanBeReplacedWithAnonymousInspection());
    myDefaultHint = InspectionGadgetsBundle.message("lambda.can.be.replaced.with.anonymous.quickfix");
  }

  public void testSimpleRunnable() {
    doTest();
  }

  public void testSimpleRunnableOnArrow() {
    doTest();
  }

  public void testWithSubstitution() {
    doTest();
  }
  
  public void testSimpleWildcard() {
    doTest();
  }

  public void testRenameParams() {
    doTest();
  }

  public void testSuperExpr() {
    doTest();
  }

  public void testInsertFinal() {
    doTest();
  }

  public void testInnerClass() {
    doTest();
  }

  public void testInnerClassWithGenericOuter() {
    doTest();
  }

  public void testCyclicInference() {
    doTest();
  }

  public void testLocalClasses() {
    doTest();
  }

  public void testNoFunctionalInterfaceFound() {
    assertQuickfixNotAvailable();
  }

  public void testAmbiguity() {
    assertQuickfixNotAvailable();
  }
  
  public void testInsideAnonymous() {
    assertQuickfixNotAvailable();
  }

  public void testEffectivelyFinal() {
    doTest();
  }

  public void testQualifyThis() {
    doTest();
  }

  public void testQualifyThis1() {
    doTest();
  }

  public void testQualifyThisAndSuperInside() {
    doTest();
  }

  public void testStaticCalls() {
    doTest();
  }

  public void testCaretAtBody() {
    assertQuickfixNotAvailable();
  }

  public void testIncorrectReturnStatementWhenLambdaIsVoidCompatibleButExpressionHasReturnValue() {
    doTest();
  }

  public void testForbidReplacementWhenParamsOrReturnWouldBeNotDenotableTypes1() {
    assertQuickfixNotAvailable();
  }

  public void testRemoveRedundantCast() {
    doTest();
  }
  
  public void testMalformedPackageName() {
    doTest();
  }

  @Override
  protected void doTest() {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    CommonCodeStyleSettings javaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    boolean oldSetting = javaSettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE;
    try {
      javaSettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
      super.doTest();
    }
    finally {
      javaSettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = oldSetting;
    }
  }

  @Override
  protected String getRelativePath() {
    return "style/lambda2anonymous";
  }
}
