// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.imports;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class StaticImportInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/imports/static_import";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  public void testSimple() {
    StaticImportInspection tool = new StaticImportInspection();
    tool.allowedClasses.add("java.util.Map");
    myFixture.enableInspections(tool);
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testMethodAllowed() {
    StaticImportInspection tool = new StaticImportInspection();
    tool.ignoreSingeMethodImports = true;
    myFixture.enableInspections(tool);
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testReportNothingOnUnresolvedImport() {
    myFixture.enableInspections(new StaticImportInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

}
