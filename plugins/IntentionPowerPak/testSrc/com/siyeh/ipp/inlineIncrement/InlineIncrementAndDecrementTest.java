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
package com.siyeh.ipp.inlineIncrement;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Pavel.Dolgov
 */
public class InlineIncrementAndDecrementTest extends IPPTestCase {

  public void testPostfixDecrementNext() {doInlineTest("--");}
  public void testPostfixDecrementPrev() {doInlineTest("--");}

  public void testPostfixIncrementNext() {doInlineTest("++");}
  public void testPostfixIncrementPrev() {doInlineTest("++");}

  public void testPrefixDecrementNext() {doInlineTest("--");}
  public void testPrefixDecrementPrev() {doInlineTest("--");}

  public void testPrefixIncrementNext() {doInlineTest("++");}
  public void testPrefixIncrementPrev() {doInlineTest("++");}

  public void testPrefixIncrementBoth() {doInlineTest("++");}
  public void testPrefixDecrementBoth() {doInlineTest("--");}

  public void testLambdaBlock() {doInlineTest("++");}
  public void testLambdaExpression() {doNegativeTest("++");}

  public void testParenthesesNext() {doInlineTest("--");}
  public void testParenthesesPrev() {doInlineTest("++");}

  private void doInlineTest(@NotNull String operator) {
    super.doTest(IntentionPowerPackBundle.message("inline.increment.intention.name", operator));
  }

  private void doNegativeTest(@SuppressWarnings("SameParameterValue") @NotNull String operator) {
    myFixture.configureByFile(getTestName(false) + ".java");
    List<IntentionAction> availableIntentions = myFixture.getAvailableIntentions();
    String action = IntentionPowerPackBundle.message("inline.increment.intention.name", operator);
    final IntentionAction intentionAction = CodeInsightTestUtil.findIntentionByText(availableIntentions, action);
    assertNull(intentionAction);
  }

  @Override
  protected String getRelativePath() {
    return "inlineIncrement";
  }
}
