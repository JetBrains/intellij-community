// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/registrationProblems/xml")
public class RegistrationProblemsInspectionXmlTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/registrationProblems/xml";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture.addClass("package com.intellij.openapi.actionSystem; public class AnAction {}");

    myFixture.addClass("package com.intellij.openapi.components; public interface ApplicationComponent {}");

    myFixture.enableInspections(new RegistrationProblemsInspection());
  }

  public void testComponentAbstractImplementation() {
    myFixture.testHighlighting("ComponentAbstractImplementation.xml",
                               "AbstractApplicationComponent.java");
  }

  public void testComponentClassNotAssignableToInterface() {
    myFixture.testHighlighting("ComponentClassNotAssignableToInterface.xml",
                               "ApplicationComponent.java");
  }

  public void testComponentMissingImplementation() {
    myFixture.testHighlighting("ComponentMissingImplementation.xml");
  }

  public void testComponentMultipleWithSameInterface() {
    myFixture.addClass("package com.intellij.openapi.module; public interface ModuleComponent {}");

    myFixture.testHighlighting("ComponentMultipleWithSameInterface.xml",
                               "ApplicationComponent.java", "ApplicationComponentInterface.java",
                               "MyModuleComponent.java", "MyModuleComponentInterface.java");
  }

  public void testComponentUnresolvedClass() {
    myFixture.testHighlighting("ComponentUnresolvedClass.xml");
  }

  public void testActionAbstractClass() {
    myFixture.testHighlighting("ActionAbstractClass.xml",
                               "MyAbstractAction.java");
  }

  public void testActionUnresolvedClass() {
    myFixture.testHighlighting("ActionUnresolvedClass.xml");
  }

  public void testActionWithoutDefaultCtor() {
    myFixture.testHighlighting("ActionWithoutDefaultCtor.xml",
                               "MyActionWithoutDefaultCtor.java");
  }

  public void testActionWrongClass() {
    myFixture.testHighlighting("ActionWrongClass.xml");
  }
}
