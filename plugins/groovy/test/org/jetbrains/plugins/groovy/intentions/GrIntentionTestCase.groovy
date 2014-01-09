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

package org.jetbrains.plugins.groovy.intentions;


import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.Function
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.intentions.base.Intention

/**
 * @author Maxim.Medvedev
 */
public abstract class GrIntentionTestCase extends LightCodeInsightFixtureTestCase {

  protected final String myHint;
  private final Class<? extends LocalInspectionTool>[] myInspections

  GrIntentionTestCase(@Nullable String hint = null, @NotNull Class<? extends LocalInspectionTool>... inspections = []) {
    myInspections = inspections
    myHint = hint
  }

  GrIntentionTestCase(@NotNull Class<? extends IntentionAction> intention) {
    myInspections = []
    myHint = intention.newInstance().text
  }

  protected void doTest(@NotNull String hint = myHint, boolean intentionExists) {
    assertNotNull(hint)
    myFixture.configureByFile(getTestName(false) + ".groovy");
    final List<IntentionAction> list = myFixture.filterAvailableIntentions(hint);
    if (intentionExists) {
      myFixture.launchAction(assertOneElement(list));
      PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();
      myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
    }
    else if (!list.empty) {
      fail StringUtil.join(list, {IntentionAction it -> it.familyName}, ',')
    }
  }

  protected void doTextTest(String before, String hint = myHint, String after, Class<? extends LocalInspectionTool>... inspections) {
    assertNotNull(hint)
    myFixture.configureByText("a.groovy", before);
    myFixture.enableInspections(inspections)
    myFixture.enableInspections(myInspections)
    final List<IntentionAction> list = myFixture.filterAvailableIntentions(hint);
    myFixture.launchAction(assertOneElement(list));
    PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();
    myFixture.checkResult(after);
  }

  protected void doAntiTest(String before, String hint = myHint, Class<? extends LocalInspectionTool>... inspections) {
    assertNotNull(hint)
    myFixture.configureByText("a.groovy", before);
    myFixture.enableInspections(inspections)
    assertEmpty(myFixture.filterAvailableIntentions(hint));
  }
}
