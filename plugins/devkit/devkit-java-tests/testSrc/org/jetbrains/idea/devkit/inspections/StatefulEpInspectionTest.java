// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/statefulEp")
public class StatefulEpInspectionTest extends PluginModuleTestCase {
  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/statefulEp";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package com.intellij.openapi.project; public class Project {}");
    myFixture.addClass("package com.intellij.psi; public class PsiElement {}");
    myFixture.addClass("package com.intellij.psi; public class PsiReference {}");
    myFixture.addClass("package com.intellij.codeInspection; public class LocalQuickFix {}");
    myFixture.addClass("package com.intellij.openapi.components; public interface ProjectComponent {}");
    myFixture.enableInspections(new StatefulEpInspection());
  }

  public void testLocalQuickFix() {
    myFixture.testHighlighting("Fix.java");
  }

  public void testNonFix() {
    setPluginXml("plugin.xml");
    myFixture.testHighlighting("NonFix.java");
  }

  public void testExt() {
    setPluginXml("plugin.xml");
    myFixture.testHighlighting("Ext.java");
  }
  
  public void testProjectComp() {
    setPluginXml("plugin.xml");
    myFixture.testHighlighting("ProjectComp.java");
  }
  
  public void testProjectService() {
    setPluginXml("plugin.xml");
    myFixture.testHighlighting("ProjectService.java");
  }

  public void testProjectConfigurable() {
    setPluginXml("plugin.xml");
    myFixture.testHighlighting("ProjectConfigurable.java");
  }

  public void testFakeFile() {
    setPluginXml("plugin.xml");
    myFixture.testHighlighting("FakeFile.java");
  }
}
