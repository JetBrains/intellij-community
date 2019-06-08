// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/pluginXmlCapitalization/")
public class PluginXmlCapitalizationInspectionTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/pluginXmlCapitalization";
  }
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new PluginXmlCapitalizationInspection());
  }

  public void testAction() {
    myFixture.testHighlighting("pluginXmlCapitalization_Action.xml");
  }
}
