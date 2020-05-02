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
package com.siyeh.ig.migration;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.InspectionGadgetsBundle;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class TryWithIdenticalCatchesTest extends LightJavaCodeInsightFixtureTestCase {
  private static final String PATH = "com/siyeh/igtest/errorhandling/try_identical_catches/";

  public void testTryIdenticalCatches() {
    doTest();
  }

  public void testNonDisjunctTypes() {
    doTest();
  }

  public void testMethodQualifier() {
    highlightTest(false);
  }

  public void testIdenticalCatchUnrelatedExceptions() {
    doTest();
  }

  public void testIdenticalCatchThreeOutOfFour() {
    doTest(true, false);
  }

  public void testIdenticalCatchWithComments() {
    doTest();
  }

  public void testIdenticalCatchWithEmptyComments() {
    doTest();
  }

  public void testIdenticalCatchWithDifferentComments() {
    doTest(false, true);
  }

  public void testIdenticalCatchDifferentCommentStyle() {
    doTest();
  }

  public void testIdenticalCatchCommentsInDifferentPlaces() {
    doTest();
  }

  public void testIdenticalNonemptyCatchWithDifferentCommentsProcessAll() {
    doTest(true, true);
  }

  public void testIdenticalNonemptyCatchWithDifferentCommentsProcessOne() {
    doTest(false, true);
  }

  public void testCatchParameterRewritten() {
    highlightTest(false);
  }

  public void doTest() {
    doTest(false, false);
  }

  public void doTest(boolean processAll, boolean checkInfos) {
    highlightTest(checkInfos);
    String name = getTestName(false);
    if (processAll) {
      PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(myFixture.getElementAtCaret(), PsiTryStatement.class);
      assertNotNull("tryStatement", tryStatement);
      PsiCatchSection[] catchSections = tryStatement.getCatchSections();
      List<IntentionAction> intentions = new ArrayList<>();
      for (PsiCatchSection section : catchSections) {
        getEditor().getCaretModel().moveToOffset(section.getTextOffset());
        intentions.addAll(myFixture.filterAvailableIntentions(InspectionGadgetsBundle.message("try.with.identical.catches.quickfix")));
      }
      assertFalse("intentions.isEmpty", intentions.isEmpty());
      for (IntentionAction intention : intentions) {
        myFixture.launchAction(intention);
      }
    }
    else {
      IntentionAction intention = myFixture.findSingleIntention(InspectionGadgetsBundle.message("try.with.identical.catches.quickfix"));
      assertNotNull(intention);
      myFixture.launchAction(intention);
    }
    myFixture.checkResultByFile(PATH + name + ".after.java");
  }

  private void highlightTest(boolean checkInfos) {
    String name = getTestName(false);
    myFixture.enableInspections(TryWithIdenticalCatchesInspection.class);
    myFixture.configureByFile(PATH + name + ".java");
    myFixture.checkHighlighting(true, checkInfos, false);
  }

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("InspectionGadgets") + "/test";
  }
}
