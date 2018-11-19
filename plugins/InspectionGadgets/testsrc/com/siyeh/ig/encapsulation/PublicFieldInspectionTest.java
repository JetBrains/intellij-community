// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.encapsulation;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class PublicFieldInspectionTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return LightInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/encapsulation/public_field";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  private void doTest() {
    PublicFieldInspection tool = new PublicFieldInspection();
    tool.ignoreEnums = true;
    tool.ignorableAnnotations.add("org.jetbrains.annotations.Nullable");
    myFixture.enableInspections(tool);
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testPublicField() {
    doTest();
  }

}
