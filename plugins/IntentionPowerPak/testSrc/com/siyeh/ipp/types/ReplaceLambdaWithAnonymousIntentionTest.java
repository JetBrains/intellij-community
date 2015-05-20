/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.siyeh.ipp.types;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

public class ReplaceLambdaWithAnonymousIntentionTest extends IPPTestCase {
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

  public void testCyclicInference() {
    doTest();
  }

  public void testLocalClasses() {
    doTest();
  }

  public void testNoFunctionalInterfaceFound() {
    assertIntentionNotAvailable();
  }

  public void testAmbiguity() {
    assertIntentionNotAvailable();
  }
  
  public void testInsideAnonymous() {
    assertIntentionNotAvailable();
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

  public void testStaticCalls() {
    doTest();
  }

  public void testIncorrectReturnStatementWhenLambdaIsVoidCompatibleButExpressionHasReturnValue() throws Exception {
    doTest();
  }

  public void testForbidReplacementWhenParamsOrReturnWouldBeNotDenotableTypes1() throws Exception {
    assertIntentionNotAvailable();
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("replace.lambda.with.anonymous.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "types/lambda2anonymous";
  }
}
