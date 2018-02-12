/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.UnclearBinaryExpressionInspection;

/**
 * @author Bas Leijdekkers
 */
public class UnclearBinaryExpressionFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UnclearBinaryExpressionInspection());
    myRelativePath = "style/unclear_binary_expression";
    myDefaultHint = InspectionGadgetsBundle.message("unclear.binary.expression.quickfix");
  }

  @Override
  protected void doTest() {
    // epic fail - two same named intentions
    final String testName = getTestName(false);
    myFixture.configureByFile(getRelativePath() + "/" + testName + ".java");
    for (IntentionAction action : myFixture.filterAvailableIntentions(myDefaultHint)) {
      while (action instanceof IntentionActionDelegate) action = ((IntentionActionDelegate)action).getDelegate();
      if (action instanceof QuickFixWrapper) {
        myFixture.launchAction(action);
        myFixture.checkResultByFile(getRelativePath() + "/" + testName + ".after.java");
        return;
      }
    }
    fail();
  }

  public void testSimpleAssignment() { doTest(); }
}
