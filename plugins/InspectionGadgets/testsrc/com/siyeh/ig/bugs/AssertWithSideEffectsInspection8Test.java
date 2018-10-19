// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class AssertWithSideEffectsInspection8Test extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return LightInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH +
           "com/siyeh/igtest/bugs/assert_with_side_effects8";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8_ANNOTATED;
  }

  private void doTest() {
    myFixture.enableInspections(new AssertWithSideEffectsInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testAssertWithSideEffects() {
    doTest();
  }
}