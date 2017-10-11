// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.components.JBList;
import com.intellij.util.PathUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/pluginXml")
public class PluginXmlDomInspectionTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/inspections/pluginXml";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new PluginXmlDomInspection());
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibrary("jblist", PathUtil.getJarPathForClass(JBList.class));
  }


  public void testOrderAttribute() {
    myFixture.testHighlighting("orderAttribute.xml");
  }
}