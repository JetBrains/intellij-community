/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;

import java.nio.file.Paths;

@TestDataPath("$CONTENT_ROOT/testData/inspections/inspectionDescription")
public class InspectionDescriptionNotFoundInspectionTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/inspections/inspectionDescription";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibrary("core-api", PathUtil.getJarPathForClass(LanguageExtensionPoint.class));
    moduleBuilder.addLibrary("analysis-api", PathUtil.getJarPathForClass(LocalInspectionEP.class));
    moduleBuilder.addLibrary("platform-resources", Paths.get(PathUtil.getJarPathForClass(LocalInspectionEP.class))
      .resolveSibling("platform-resources").toString());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(InspectionDescriptionNotFoundInspection.class);
  }

  public void testHighlightingForDescription() {
    myFixture.testHighlighting("MyInspection.java");
  }

  public void testHighlightingForDescriptionCustomShortName() {
    myFixture.testHighlighting("MyInspectionCustomShortName.java");
  }

  public void testOverridePathMethod() {
    myFixture.testHighlighting("MyOverridePathMethodInspection.java");
  }

  public void testWithDescription() {
    myFixture.copyDirectoryToProject("inspectionDescriptions", "inspectionDescriptions");
    myFixture.testHighlighting("MyWithDescriptionInspection.java");
  }

  public void testWithDescriptionAndShortNameInBase() {
    myFixture.copyDirectoryToProject("inspectionDescriptions", "inspectionDescriptions");
    myFixture.testHighlighting("MyWithDescriptionAndShortNameInBaseInspection.java");
  }

  public void testWithDescriptionCustomShortName() {
    myFixture.copyDirectoryToProject("inspectionDescriptions", "inspectionDescriptions");
    myFixture.testHighlighting("MyWithDescriptionCustomShortNameInspection.java");
  }

  public void testWithDescriptionXmlRegisteredNotFound() {
    myFixture.copyDirectoryToProject("inspectionDescriptions", "inspectionDescriptions");
    myFixture.copyDirectoryToProject("resources", "resources");
    myFixture.testHighlighting("MyRegisteredInspection.java");
  }

  public void testWithDescriptionXmlRegisteredOk() {
    myFixture.copyDirectoryToProject("inspectionDescriptions", "inspectionDescriptions");
    myFixture.copyDirectoryToProject("resources", "resources");
    myFixture.testHighlighting("MyRegisteredCorrectlyInspection.java");
  }

  public void testQuickFix() {
    myFixture.configureByFile("MyQuickFixInspection.java");
    IntentionAction item = myFixture.findSingleIntention("Create description file MyQuickFix.html");
    myFixture.launchAction(item);

    VirtualFile path = myFixture.findFileInTempDir("inspectionDescriptions/MyQuickFix.html");
    assertNotNull(path);
    assertTrue(path.exists());
  }
}
