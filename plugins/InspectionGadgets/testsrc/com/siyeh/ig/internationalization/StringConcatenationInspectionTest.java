// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.internationalization;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class StringConcatenationInspectionTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return LightInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/internationalization/string_concatenation";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  private void doTest() {
    StringConcatenationInspection inspection = new StringConcatenationInspection();
    inspection.ignoreThrowableArguments = true;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testStringConcatenation() {
    doTest();
  }

}
