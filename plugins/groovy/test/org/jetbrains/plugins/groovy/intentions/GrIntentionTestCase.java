// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Maxim.Medvedev
 */
public abstract class GrIntentionTestCase extends LightJavaCodeInsightFixtureTestCase {
  protected final String myHint;
  private final Class<? extends LocalInspectionTool>[] myInspections;

  public GrIntentionTestCase(@Nullable String hint, @NotNull Class<? extends LocalInspectionTool> @NotNull ... inspections) {
    myInspections = inspections;
    myHint = hint;
  }

  public GrIntentionTestCase() {
    this(null);
  }

  protected void doTest(@NotNull String hint, boolean intentionShouldBeAvailable) {
    assertNotNull(hint);
    myFixture.configureByFile(getTestName(false) + ".groovy");
    final List<IntentionAction> list = myFixture.filterAvailableIntentions(hint);
    if (intentionShouldBeAvailable) {
      if (list.size() != 1) {
        fail("Intention not found among " +
                      myFixture.getAvailableIntentions().stream().map(IntentionAction::getText).collect(Collectors.joining(", ")));
      }

      myFixture.launchAction(assertOneElement(list));
      PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
      myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
    }
    else if (!list.isEmpty()) {
      fail(StringUtil.join(list, IntentionAction::getFamilyName, ","));
    }
  }

  protected void doTest(boolean intentionShouldBeAvailable) {
    doTest(myHint, intentionShouldBeAvailable);
  }

  protected void doTextTest(String before, String hint, String after, Class<? extends LocalInspectionTool>... inspections) {
    assertNotNull(hint);
    myFixture.configureByText("a.groovy", before);
    myFixture.enableInspections(inspections);
    myFixture.enableInspections(myInspections);
    final List<IntentionAction> list = myFixture.filterAvailableIntentions(hint);
    if (list.size() != 1) {
      fail("Intention not found among " +
                    myFixture.getAvailableIntentions().stream().map(IntentionAction::getText).collect(Collectors.joining(", ")));
    }

    myFixture.launchAction(assertOneElement(list));
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    myFixture.checkResult(after);
  }

  protected void doTextTest(String before, String after, Class<? extends LocalInspectionTool>... inspections) {
    doTextTest(before, myHint, after, inspections);
  }

  protected void doAntiTest(String before, String hint, Class<? extends LocalInspectionTool>... inspections) {
    assertNotNull(hint);
    myFixture.configureByText("a.groovy", before);
    myFixture.enableInspections(inspections);
    myFixture.enableInspections(myInspections);
    assertEmpty(myFixture.filterAvailableIntentions(hint));
  }

  protected void doAntiTest(String before, Class<? extends LocalInspectionTool>... inspections) {
    doAntiTest(before, myHint, inspections);
  }
}
