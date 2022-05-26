// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

public class TryWithIdenticalCatchesTest extends LightJavaCodeInsightFixtureTestCase {
  private static final String PATH = "com/siyeh/igtest/errorhandling/try_identical_catches/";

  public void testTryIdenticalCatches() {
    doTest();
  }

  public void testNonDisjunctTypes() {
    doTest();
  }

  public void testMethodQualifier() {
    highlightTest(false, false);
  }

  public void testIdenticalCatchUnrelatedExceptions() {
    doTest();
  }

  public void testIdenticalCatchThreeOutOfFour() {
    doTest(true, false, false);
  }

  public void testIdenticalCatchWithComments() {
    doTest();
  }

  public void testIdenticalCatchWithEmptyComments() {
    doTest();
  }

  public void testIdenticalCatchWithDifferentComments() {
    doTest(false, true, false);
  }

  public void testIdenticalCatchDifferentCommentStyle() {
    doTest();
  }

  public void testIdenticalCatchCommentsInDifferentPlaces() {
    doTest();
  }

  public void testIdenticalNonemptyCatchWithDifferentCommentsProcessAll() {
    doTest(true, true, false);
  }

  public void testIdenticalNonemptyCatchWithDifferentCommentsProcessOne() {
    doTest(false, true, false);
  }

  public void testIdenticalNonemptyCatchWithDifferentCommentsStrict() {
    highlightTest(true, true);
  }

  public void testCatchParameterRewritten() {
    highlightTest(false, false);
  }

  public void doTest() {
    doTest(false, false, false);
  }

  private void doTest(boolean processAll, boolean checkInfos, boolean strictComments) {
    highlightTest(checkInfos, strictComments);
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

  private void highlightTest(boolean checkInfos, boolean strictComments) {
    String name = getTestName(false);
    TryWithIdenticalCatchesInspection inspection = new TryWithIdenticalCatchesInspection();
    inspection.ignoreBlocksWithDifferentComments = strictComments;
    myFixture.enableInspections(inspection);
    myFixture.configureByFile(PATH + name + ".java");
    myFixture.checkHighlighting(true, checkInfos, false);
  }

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("InspectionGadgets") + "/test";
  }
}
