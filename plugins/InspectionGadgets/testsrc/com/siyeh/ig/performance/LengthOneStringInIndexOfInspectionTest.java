// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.performance;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class LengthOneStringInIndexOfInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/performance/length_one_strings_in_indexof";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  private void doTest() {
    LengthOneStringInIndexOfInspection inspection = new LengthOneStringInIndexOfInspection();
    myFixture.enableInspections(inspection);
    ProjectInspectionProfileManager.getInstance(myFixture.getProject()).getCurrentProfile()
      .setErrorLevel(HighlightDisplayKey.find(inspection.getShortName()), HighlightDisplayLevel.WARNING, myFixture.getProject());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testLengthOneStringInIndexOf() {
    doTest();
  }

}
