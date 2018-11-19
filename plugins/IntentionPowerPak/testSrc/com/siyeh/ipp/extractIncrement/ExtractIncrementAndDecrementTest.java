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
package com.siyeh.ipp.extractIncrement;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Pavel.Dolgov
 */
public class ExtractIncrementAndDecrementTest extends IPPTestCase {

  public void testPostfixDecrement() {doExtractTest("--");}

  public void testPostfixIncrement() {doExtractTest("++");}

  public void testPrefixDecrement() {doExtractTest("--");}

  public void testPrefixIncrement() {doExtractTest("++");}

  public void testSingleDoWhileBody() {doExtractTest("++");}

  public void testDecrementInForUpdate() {doNegativeTest("--");}

  public void testTwoIncrementsInForUpdate() {doNegativeTest("++");}

  private void doExtractTest(@NotNull String operator) {
    super.doTest(getMessage(operator));
  }

  private void doNegativeTest(@NotNull String operator) {
    myFixture.configureByFile(getTestName(false) + ".java");
    IntentionAction intentionAction = CodeInsightTestUtil.findIntentionByText(myFixture.getAvailableIntentions(), getMessage(operator));
    assertNull(intentionAction + " is not expected here", intentionAction);
  }

  @NotNull
  private static String getMessage(@NotNull String operator) {
    return IntentionPowerPackBundle.message("extract.increment.intention.name", operator);
  }

  @Override
  protected String getRelativePath() {
    return "extractIncrement";
  }
}
