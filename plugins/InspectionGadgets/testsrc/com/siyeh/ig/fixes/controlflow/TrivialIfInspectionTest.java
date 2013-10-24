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
package com.siyeh.ig.fixes.controlflow;

import com.intellij.codeInsight.intention.IntentionAction;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.TrivialIfInspection;

import java.util.List;

public class TrivialIfInspectionTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new TrivialIfInspection());
    myRelativePath = "controlflow/trivialIf";
  }

  public void testComments() {
    final String testName = getTestName(false);
    myFixture.configureByFile(getRelativePath() + "/" + testName + ".java");
    final String message = InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix");
    final List<IntentionAction> actions =
      myFixture.filterAvailableIntentions(message);
    assertFalse("No actions available", actions.isEmpty());
    for (IntentionAction action : actions) {
      if (action.getText().equals(message)) {
        myFixture.launchAction(action);
        myFixture.checkResultByFile(getRelativePath() + "/" + testName + ".after.java");
        break;
      }
    }
  }
}