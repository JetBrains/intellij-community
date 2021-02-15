// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class CStyleArrayDeclarationInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/style/cstyle_array_declaration";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_15;
  }

  private void doTest() {
    myFixture.enableInspections(new CStyleArrayDeclarationInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testCStyleArrayDeclaration() {
    doTest();
  }

}
