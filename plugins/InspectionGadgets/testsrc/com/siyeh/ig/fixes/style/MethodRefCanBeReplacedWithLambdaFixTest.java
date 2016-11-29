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

  public void testRedundantCast() throws Exception {
    doTest();
  }

  public void testStaticMethodRef() throws Exception {
    doTest();
  }

  public void testThisRefs() throws Exception {
    doTest();
  }

  public void testSuperRefs() throws Exception {
    doTest();
  }

  public void testExprRefs() throws Exception {
    doTest();
  }

  public void testReceiver() throws Exception {
    doTest();
  }

  public void testNewRefs() throws Exception {
    doTest();
  } 

  public void testNewRefsDefaultConstructor() throws Exception {
    doTest();
  }

  public void testNewRefsInnerClass() throws Exception {
    doTest();
  }

  public void testNewRefsStaticInnerClass() throws Exception {
    doTest();
  }

  public void testNewRefsInference() throws Exception {
    doTest();
  }

  public void testNewRefsInference1() throws Exception {
    doTest();
  }

  public void testAmbiguity() throws Exception {
    doTest();
  }

  public void testSubst() throws Exception {
    doTest();
  }

  public void testTypeElementOnTheLeft() throws Exception {
    doTest();
  }

  public void testNewDefaultConstructor() throws Exception {
    doTest();
  }

  public void testArrayConstructorRef() throws Exception {
    doTest();
  }

  public void testArrayConstructorRef2Dim() throws Exception {
    doTest();
  }

  public void testArrayMethodRef() throws Exception {
    doTest();
  }

  public void testArrayConstructorRefUniqueParamName() throws Exception {
    doTest();
  }

  public void testNameConflicts() throws Exception {
    doTest();
  }

  public void testIntroduceVariableForSideEffectQualifier() throws Exception {
    doTest();
  }

  public void testCollapseToExpressionLambdaWhenCast() throws Exception {
    doTest();
  }

  public void testPreserveExpressionQualifier() throws Exception {
    doTest();
  }
}
