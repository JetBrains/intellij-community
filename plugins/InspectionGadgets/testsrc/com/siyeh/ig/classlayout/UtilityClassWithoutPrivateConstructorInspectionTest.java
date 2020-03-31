// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.classlayout;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class UtilityClassWithoutPrivateConstructorInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/classlayout/utility_class_without_private_constructor";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  private void doTest() {
    UtilityClassWithoutPrivateConstructorInspection inspection = new UtilityClassWithoutPrivateConstructorInspection();
    inspection.ignoreClassesWithOnlyMain = true;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testUtilityClassWithoutPrivateConstructorInspection() {
    doTest();
  }

}
