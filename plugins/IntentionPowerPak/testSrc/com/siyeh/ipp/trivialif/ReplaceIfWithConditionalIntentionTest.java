/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ipp.trivialif;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see com.siyeh.ipp.trivialif.ReplaceIfWithConditionalIntention
 */
public class ReplaceIfWithConditionalIntentionTest extends IPPTestCase {

  public void testReturnValueWithDiamonds() { doTest(); }
  public void testReplaceableAssignmentsWithDiamonds() { doTest(); }
  public void testReplaceableAssignmentsWithDiamondsLeave() { doTest(); }
  public void testConditionalCondition() { doTest(); }
  public void testComments() { doTest(); }
  public void testCommentsInCondition() { doTest(); }
  public void testInsideLambda() { doTest(); }

  public void testInsideLambda1() {
    doTest();
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("replace.if.with.conditional.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "trivialif/replaceIfWithConditional";
  }
}
