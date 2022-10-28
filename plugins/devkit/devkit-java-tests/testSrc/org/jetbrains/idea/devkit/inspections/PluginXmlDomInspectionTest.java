// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/registrationProblems/xml")
public class PluginXmlDomInspectionTest extends PluginXmlDomInspectionTestBase {
  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/registrationProblems/xml";
  }

  public void testComponentAbstractImplementation() {
    myFixture.testHighlighting("ComponentAbstractImplementation.xml",
                               "AbstractApplicationComponent.java");
  }

  public void testComponentMissingImplementation() {
    myFixture.testHighlighting("ComponentMissingImplementation.xml");
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

  public void testComponentClassNotAssignableToInterface() {
    myFixture.testHighlighting("ComponentClassNotAssignableToInterface.xml",
                               "ApplicationComponent.java");
  }
}
