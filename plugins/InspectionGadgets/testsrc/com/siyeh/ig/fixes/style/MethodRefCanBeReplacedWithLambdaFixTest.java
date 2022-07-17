// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    ModuleRootModificationUtil.setModuleSdk(getModule(), IdeaTestUtil.getMockJdk18());
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
  
  public void testIntroduceVariableForNewInQualifier() {
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
  public void testCaptureOnInvalidatedReference() {
    doTest();
  }
  public void testCaptureOnInvalidatedReference1() {
    doTest();
  }

  public void testSpecifyFormalParameterTypesWhenMethodReferenceWasExactAndTypeOfParameterIsUnknown() {
    doTest();
  }

  public void testNewArrayMethodReferenceHasNoSideEffects() { doTest(); }
  public void testExplicitTypeRequired() { doTest(); }

  public void testNestedClassReference(){
    doTest();
  }

  public void testLocalClassReference(){
    doTest();
  }

  public void testEnsureNoConversionIsSuggestedWhenLambdaWithoutCantBeInferredAndFormalParametersAreNotDenotable() {
    assertQuickfixNotAvailable();
  }
  
  public void testBrokenPrimitiveArray() {
    doTest();
  }
}
