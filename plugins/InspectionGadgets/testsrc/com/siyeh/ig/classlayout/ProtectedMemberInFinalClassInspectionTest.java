// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProtectedMemberInFinalClassInspectionTest extends LightJavaCodeInsightFixtureTestCase5 {
  ProtectedMemberInFinalClassInspectionTest() {
    super(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  }

  @BeforeEach
  void setUp() {
    getFixture().enableInspections(new ProtectedMemberInFinalClassInspection());
  }

  @Override
  public @NotNull String getRelativePath() {
    return LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/classlayout/protected_member_in_final_class";
  }

  @Test
  void testProtectedMemberInFinalClass() {
    doTest();
  }

  private void doTest() {
    getFixture().testHighlighting(getTestName(false) + ".java");
    String inspectionName = InspectionGadgetsBundle.message("protected.member.in.final.class.display.name");
    IntentionAction intention = getFixture().findSingleIntention(InspectionsBundle.message("fix.all.inspection.problems.in.file", inspectionName));
    assertNotNull(intention);
    getFixture().launchAction(intention);
    getFixture().checkResultByFile(getTestName(false) + ".after.java");
  }
}
