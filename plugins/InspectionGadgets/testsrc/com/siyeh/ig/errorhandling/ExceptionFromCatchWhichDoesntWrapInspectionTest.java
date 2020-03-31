// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.errorhandling;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class ExceptionFromCatchWhichDoesntWrapInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/errorhandling/exception_from_catch";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    // SQL is necessary
    return JAVA_1_7;
  }

  private void doTest() {
    ExceptionFromCatchWhichDoesntWrapInspection tool = new ExceptionFromCatchWhichDoesntWrapInspection();
    tool.ignoreCantWrap = true;
    myFixture.enableInspections(tool);
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testExceptionFromCatchWhichDoesntWrap() {
    doTest();
  }

}
