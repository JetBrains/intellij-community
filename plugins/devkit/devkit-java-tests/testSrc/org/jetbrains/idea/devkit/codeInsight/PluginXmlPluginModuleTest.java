// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.codeInsight;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.jetbrains.idea.devkit.inspections.PluginModuleTestCase;
import org.jetbrains.idea.devkit.inspections.PluginXmlDomInspection;

@TestDataPath("$CONTENT_ROOT/testData/codeInsight")
public class PluginXmlPluginModuleTest extends PluginModuleTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new PluginXmlDomInspection());
  }

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "codeInsight";
  }

  public void testPluginWithoutVersionInPluginModule() {
    myFixture.testHighlighting(false, false, false, "pluginWithoutVersionInPluginModule.xml");
  }
}
