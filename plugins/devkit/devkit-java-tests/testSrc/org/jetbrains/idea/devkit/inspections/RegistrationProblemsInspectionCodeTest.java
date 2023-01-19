// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/registrationProblems/code")
public class RegistrationProblemsInspectionCodeTest extends RegistrationProblemsInspectionCodeTestBase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/registrationProblems/code";
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
