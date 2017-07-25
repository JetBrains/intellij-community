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
package com.siyeh.ipp.conditional;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see ReplaceConditionalWithIfIntention
 */
public class ReplaceConditionalWithIfTest extends IPPTestCase {

  public void testConditionalAsArgument() { doTest(); }
  public void testComment() { doTest(); }
  public void testParentheses() { doTest(); }
  public void testConditionalInIf() { doTest(); }
  public void testConditionalInBinaryExpression() { doTest(); }
  public void testArrayInitializer() { doTest(); }
  public void testInsideExprLambda() { doTest(); }
  public void testInsideExprLambdaWithParams() { doTest(); }
  public void testCastNeeded() { doTest(); }

  public void testBrokenCode() { assertIntentionNotAvailable(); }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message(
      "replace.conditional.with.if.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "conditional/withIf";
  }
}
