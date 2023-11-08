// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.intentions


import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

/**
 * @author Maxim.Medvedev
 */
abstract class GrIntentionTestCase extends LightJavaCodeInsightFixtureTestCase {
  protected final String myHint
  private final Class<? extends LocalInspectionTool>[] myInspections

  GrIntentionTestCase(@Nullable String hint = null, @NotNull Class<? extends LocalInspectionTool>... inspections = []) {
    myInspections = inspections
    myHint = hint
  }

  protected void doTest(@NotNull String hint = myHint, boolean intentionShouldBeAvailable) {
    assertNotNull(hint)
    myFixture.configureByFile(getTestName(false) + ".groovy")
    final List<IntentionAction> list = myFixture.filterAvailableIntentions(hint)
    if (intentionShouldBeAvailable) {
      if (list.size() != 1) {
        fail("Intention not found among " +
             myFixture.getAvailableIntentions().collect { intention -> intention.text }.join(", "))
      }
      myFixture.launchAction(assertOneElement(list))
      PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
      myFixture.checkResultByFile(getTestName(false) + "_after.groovy")
    }
    else if (!list.empty) {
      fail StringUtil.join(list, {IntentionAction it -> it.familyName}, ',')
    }
  }

  protected void doTextTest(String before, String hint = myHint, String after, Class<? extends LocalInspectionTool>... inspections) {
    assertNotNull(hint)
    myFixture.configureByText("a.groovy", before)
    myFixture.enableInspections(inspections)
    myFixture.enableInspections(myInspections)
    final List<IntentionAction> list = myFixture.filterAvailableIntentions(hint)
    if (list.size() != 1) {
      fail("Intention not found among " +
           myFixture.getAvailableIntentions().collect { intention -> intention.text }.join(", "))
    }
    myFixture.launchAction(assertOneElement(list))
    PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
    myFixture.checkResult(after)
  }

  protected void doAntiTest(String before, String hint = myHint, Class<? extends LocalInspectionTool>... inspections) {
    assertNotNull(hint)
    myFixture.configureByText("a.groovy", before)
    myFixture.enableInspections(inspections)
    myFixture.enableInspections(myInspections)
    assertEmpty(myFixture.filterAvailableIntentions(hint))
  }
}
