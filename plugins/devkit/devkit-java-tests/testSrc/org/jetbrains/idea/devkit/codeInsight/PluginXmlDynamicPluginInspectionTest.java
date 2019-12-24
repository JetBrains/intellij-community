// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.codeInsight;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.jetbrains.idea.devkit.inspections.PluginXmlDynamicPluginInspection;

@TestDataPath("$CONTENT_ROOT/testData/codeInsight/pluginXmlDynamicPluginInspection")
public class PluginXmlDynamicPluginInspectionTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "codeInsight/pluginXmlDynamicPluginInspection";
  }

  public void testHighlighting() {
    myFixture.enableInspections(new PluginXmlDynamicPluginInspection());
    myFixture.testHighlighting("pluginXmlDynamicPluginInspection-highlighting.xml");
  }

  public void testUsingExtensionPoints() {
    final PluginXmlDynamicPluginInspection inspection = new PluginXmlDynamicPluginInspection();
    inspection.highlightNonDynamicEPUsages = true;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting("pluginXmlDynamicPluginInspection-usingExtensionPoints.xml");
  }
}
