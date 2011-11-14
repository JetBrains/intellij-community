/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public abstract class GrIntentionTestCase extends LightCodeInsightFixtureTestCase {
  protected void doTest(String hint, boolean intentionExists) {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    final List<IntentionAction> list = myFixture.filterAvailableIntentions(hint);
    if (intentionExists) {
      myFixture.launchAction(assertOneElement(list));
      PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
      myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
    }
    else {
      if (list.size() > 0) {
        StringBuilder text = new StringBuilder("available intentions:");
        for (IntentionAction intentionAction : list) {
          text.append(intentionAction.getFamilyName()).append(", ");
        }
        fail(text.toString());
      }
    }
  }

  protected void doTextTest(String before, String hint, String after) {
    myFixture.configureByText("a.groovy", before);
    final List<IntentionAction> list = myFixture.filterAvailableIntentions(hint);
    myFixture.launchAction(assertOneElement(list));
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    myFixture.checkResult(after);
  }

  protected void doAntiTest(String before, String hint) {
    myFixture.configureByText("a.groovy", before);
    assertEmpty(myFixture.filterAvailableIntentions(hint));
  }
}
