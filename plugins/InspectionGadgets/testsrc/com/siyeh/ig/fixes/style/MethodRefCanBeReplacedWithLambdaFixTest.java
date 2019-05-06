/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.MethodRefCanBeReplacedWithLambdaInspection;

public class MethodRefCanBeReplacedWithLambdaFixTest extends IGQuickFixesTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ModuleRootModificationUtil.setModuleSdk(myModule, IdeaTestUtil.getMockJdk18());
    myFixture.enableInspections(new MethodRefCanBeReplacedWithLambdaInspection());
    myDefaultHint = InspectionGadgetsBundle.message("method.ref.can.be.replaced.with.lambda.quickfix");
  }

  @Override
  protected String getRelativePath() {
    return "style/methodRefs2lambda";
  }

  public void testRedundantCast() {
    doTest();
  }

  public void testStaticMethodRef() {
    doTest();
  }

  public void testThisRefs() {
    doTest();
  }

  public void testSuperRefs() {
    doTest();
  }

  public void testExprRefs() {
    doTest();
  }

  public void testReceiver() {
    doTest();
  }

  public void testNewRefs() {
    doTest();
  } 

  public void testNewRefsDefaultConstructor() {
    doTest();
  }

  public void testNewRefsInnerClass() {
    doTest();
  }

  public void testNewRefsStaticInnerClass() {
    doTest();
  }

  public void testNewRefsInference() {
    doTest(myDefaultHint);
  }

  public void testNewRefsInference1() {
    doTest();
  }

  public void testAmbiguity() {
    doTest();
  }

  public void testSubst() {
    doTest();
  }

  public void testTypeElementOnTheLeft() {
    doTest();
  }

  public void testNewDefaultConstructor() {
    doTest();
  }

  public void testArrayConstructorRef() {
    doTest();
  }

  public void testArrayConstructorRef2Dim() {
    doTest();
  }

  public void testArrayMethodRef() {
    doTest(myDefaultHint  );
  }

  public void testArrayConstructorRefUniqueParamName() {
    doTest();
  }

  public void testNameConflicts() {
    doTest();
  }

  public void testIntroduceVariableForSideEffectQualifier() {
    doTest(myDefaultHint + " (side effects)");
  }

  public void testCollapseToExpressionLambdaWhenCast() {
    doTest();
  }

  public void testPreserveExpressionQualifier() {
    doTest();
  }

  public void testNoUnderscoreInLambdaParameterName() {
    doTest();
  }

  public void testNoCastWhereCaptureArgIsExpected() {
    doTest();
  }

  public void testSpecifyFormalParameterTypesWhenMethodReferenceWasExactAndTypeOfParameterIsUnknown() {
    doTest();
  }

  public void testNewArrayMethodReferenceHasNoSideEffects() { doTest(); }
  public void testExplicitTypeRequired() { doTest(); }

  public void testEnsureNoConversionIsSuggestedWhenLambdaWithoutCantBeInferredAndFormalParametersAreNotDenotable() {
    assertQuickfixNotAvailable();
  }
}
