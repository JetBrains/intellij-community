// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.idea.devkit.inspections.PluginXmlDomInspectionTestBase;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/registrationProblems/xml/components")
public class KtPluginXmlDomInspectionComponentHighlightingTest extends PluginXmlDomInspectionTestBase {
  @Override
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/registrationProblems/xml/components";
  }

  public void testComponentAbstractImplementation() {
    myFixture.testHighlighting(
      "ComponentAbstractImplementation.xml",
      "AbstractApplicationComponent.kt");
  }

  public void testComponentMissingImplementation() {
    myFixture.testHighlighting(
      "ComponentMissingImplementation.xml");
  }

  public void testComponentUnresolvedClass() {
    myFixture.testHighlighting("ComponentUnresolvedClass.xml");
  }

  public void testComponentClassNotAssignableToInterface() {
    myFixture.testHighlighting(
      "ComponentClassNotAssignableToInterface.xml",
      "ApplicationComponent.kt");
  }

  public void testComponentMultipleWithSameInterface() {
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
