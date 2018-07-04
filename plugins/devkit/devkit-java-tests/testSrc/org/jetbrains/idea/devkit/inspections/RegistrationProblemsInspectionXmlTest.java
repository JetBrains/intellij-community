/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
