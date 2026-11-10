// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.codeInsight;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.jetbrains.idea.devkit.inspections.PluginXmlDynamicPluginInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestDataPath("$CONTENT_ROOT/testData/codeInsight/pluginXmlDynamicPluginInspection")
@TestApplication
public class PluginXmlDynamicPluginInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, DevkitJavaTestsUtil.TESTDATA_PATH + "codeInsight/pluginXmlDynamicPluginInspection");

  @Test
  public void testHighlighting() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new PluginXmlDynamicPluginInspection());
    myFixture.testHighlighting("pluginXmlDynamicPluginInspection-highlighting.xml");
  }

  @Test
  public void testUsingExtensionPoints() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PluginXmlDynamicPluginInspection inspection = new PluginXmlDynamicPluginInspection();
    inspection.highlightNonDynamicEPUsages = true;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting("pluginXmlDynamicPluginInspection-usingExtensionPoints.xml");
  }
}
