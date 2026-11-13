// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.inspections.PluginXmlDomInspectionFixture;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.idea.devkit.inspections.PluginXmlDomInspectionFixtureKt.pluginXmlDomInspectionFixture;

@TestDataPath("$CONTENT_ROOT/testData/inspections/registrationProblems/xml/components")
@TestApplication
public class KtPluginXmlDomInspectionComponentHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<PluginXmlDomInspectionFixture> pluginXml =
    pluginXmlDomInspectionFixture(projectFixture, DevkitKtTestsUtil.TESTDATA_PATH + "inspections/registrationProblems/xml/components");

  @Test
  public void testComponentAbstractImplementation() {
    LightJavaCodeInsightTestFixture myFixture = pluginXml.get().getFixture();
    myFixture.testHighlighting(
      "ComponentAbstractImplementation.xml",
      "AbstractApplicationComponent.kt");
  }

  @Test
  public void testComponentMissingImplementation() {
    LightJavaCodeInsightTestFixture myFixture = pluginXml.get().getFixture();
    myFixture.testHighlighting(
      "ComponentMissingImplementation.xml");
  }

  @Test
  public void testComponentUnresolvedClass() {
    LightJavaCodeInsightTestFixture myFixture = pluginXml.get().getFixture();
    myFixture.testHighlighting("ComponentUnresolvedClass.xml");
  }

  @Test
  public void testComponentClassNotAssignableToInterface() {
    LightJavaCodeInsightTestFixture myFixture = pluginXml.get().getFixture();
    myFixture.testHighlighting(
      "ComponentClassNotAssignableToInterface.xml",
      "ApplicationComponent.kt");
  }

  @Test
  public void testComponentMultipleWithSameInterface() {
    LightJavaCodeInsightTestFixture myFixture = pluginXml.get().getFixture();
    myFixture.addClass("package com.intellij.openapi.module; public interface ModuleComponent {}");

    myFixture.testHighlighting(
      "ComponentMultipleWithSameInterface.xml",
      "ApplicationComponent.kt",
      "ApplicationComponent2.kt",
      "ApplicationComponentInterface.kt",
      "MyModuleComponent.kt",
      "MyModuleComponent2.kt",
      "MyModuleComponent3.kt",
      "MyModuleComponentInterface.kt");
  }
}
