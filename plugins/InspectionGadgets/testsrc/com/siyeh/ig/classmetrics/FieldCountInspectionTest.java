// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.classmetrics;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class FieldCountInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/classmetrics/field_count";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  private void doTest() {
    FieldCountInspection inspection = new FieldCountInspection();
    inspection.m_limit = 5;
    inspection.myCountEnumConstants = false;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testFieldCount() {
    doTest();
  }

}
