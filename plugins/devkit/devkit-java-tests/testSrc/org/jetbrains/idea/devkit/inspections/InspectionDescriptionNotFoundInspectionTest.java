// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/inspectionDescription")
public class InspectionDescriptionNotFoundInspectionTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/inspectionDescription";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibrary("core-api", PathUtil.getJarPathForClass(LanguageExtensionPoint.class));
    moduleBuilder.addLibrary("analysis-api", PathUtil.getJarPathForClass(LocalInspectionEP.class));
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
    myFixture.testHighlighting("MyWithDescriptionCustomConstantShortNameInspection.java");
    myFixture.testHighlighting("MyWithDescriptionCustomMethodInvokingShortNameInspection.java");
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
