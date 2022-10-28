// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.idea.devkit.inspections.RegistrationProblemsInspectionXmlTestBase;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/registrationProblems/xml")
public class KtRegistrationProblemsInspectionXmlTest extends RegistrationProblemsInspectionXmlTestBase {
  @Override
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/registrationProblems/xml";
  }

  public void testComponentMultipleWithSameInterface() {
    myFixture.addClass("package com.intellij.openapi.module; public interface ModuleComponent {}");

    myFixture.testHighlighting("ComponentMultipleWithSameInterface.xml",
                               "ApplicationComponent.kt",
                               "ApplicationComponentInterface.kt",
                               "MyModuleComponent.kt",
                               "MyModuleComponentInterface.kt");
  }
}
