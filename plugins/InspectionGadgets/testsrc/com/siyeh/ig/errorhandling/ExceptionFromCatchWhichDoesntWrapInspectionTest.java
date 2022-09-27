// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    return JAVA_17;
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
