// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.PluginXmlDomInspectionTestBase;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode;
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider;
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProviderKt;

@TestDataPath("$CONTENT_ROOT/testData/inspections/registrationProblems/xml/components")
public class KtPluginXmlDomInspectionComponentHighlightingTest extends PluginXmlDomInspectionTestBase implements
                                                                                                      ExpectedPluginModeProvider {
  @Override
  public @NotNull KotlinPluginMode getPluginMode() {
    return KotlinPluginMode.K2;
  }

  @Override
  protected void setUp() throws Exception {
    ExpectedPluginModeProviderKt.setUpWithKotlinPlugin(this, getTestRootDisposable(), () -> super.setUp());
  }

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
