// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/registrationProblems/xml/components")
public class PluginXmlDomInspectionComponentHighlightingTest extends PluginXmlDomInspectionTestBase {
  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/registrationProblems/xml/components";
  }

  public void testComponentAbstractImplementation() {
    myFixture.testHighlighting(
      "ComponentAbstractImplementation.xml",
      "AbstractApplicationComponent.java");
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
      "ApplicationComponent.java");
  }

  public void testComponentMultipleWithSameInterface() {
    myFixture.addClass("package com.intellij.openapi.module; public interface ModuleComponent {}");

    myFixture.testHighlighting(
      "ComponentMultipleWithSameInterface.xml",
      "ApplicationComponent.java",
      "ApplicationComponent2.java",
      "ApplicationComponentInterface.java",
      "MyModuleComponent.java",
      "MyModuleComponent2.java",
      "MyModuleComponent3.java",
      "MyModuleComponentInterface.java");
  }
}
