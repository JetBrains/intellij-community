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
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/registrationProblems/code")
public class RegistrationProblemsInspectionCodeTest extends PluginModuleTestCase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/registrationProblems/code";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture.addClass("package com.intellij.openapi.actionSystem; public class AnAction {}");

    myFixture.addClass("package com.intellij.openapi.components; public interface ApplicationComponent {}");

    myFixture.enableInspections(new RegistrationProblemsInspection());
  }

  public void testComponentAbstractImplementation() {
    setPluginXml("ComponentAbstractImplementation.xml");
    myFixture.testHighlighting("AbstractApplicationComponent.java");
  }

  public void testApplicationComponentMustNotInherit() {
    setPluginXml("ApplicationComponentMustNotInherit.xml");
    myFixture.testHighlighting("MyApplicationComponentMustNotInherit.java");
  }

  public void testActionAbstractClass() {
    setPluginXml("ActionAbstractClass.xml");
    myFixture.testHighlighting("MyAbstractAction.java");
  }

  public void testActionWithoutDefaultCtor() {
    setPluginXml("ActionWithoutDefaultCtor.xml");
    myFixture.testHighlighting("MyActionWithoutDefaultCtor.java");
  }

  public void testActionWrongClass() {
    setPluginXml("ActionWrongClass.xml");
    myFixture.testHighlighting("MyActionWrongClass.java");
  }
}
