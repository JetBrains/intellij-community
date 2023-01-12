// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.idea.devkit.inspections.RegistrationProblemsInspectionCodeTestBase;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/registrationProblems/code")
public class KtRegistrationProblemsInspectionCodeTest extends RegistrationProblemsInspectionCodeTestBase {

  @Override
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/registrationProblems/code";
  }

  public void testComponentAbstractImplementation() {
    setPluginXml("ComponentAbstractImplementation.xml");
    myFixture.testHighlighting("AbstractApplicationComponent.kt");
  }

  public void testApplicationComponentMustNotInherit() {
    setPluginXml("ApplicationComponentMustNotInherit.xml");
    myFixture.testHighlighting("MyApplicationComponentMustNotInherit.kt");
  }

  public void testActionAbstractClass() {
    setPluginXml("ActionAbstractClass.xml");
    myFixture.testHighlighting("MyAbstractAction.kt");
  }

  public void testActionWithoutDefaultCtor() {
    setPluginXml("ActionWithoutDefaultCtor.xml");
    myFixture.testHighlighting("MyActionWithoutDefaultCtor.kt");
  }

  public void testActionWrongClass() {
    setPluginXml("ActionWrongClass.xml");
    myFixture.testHighlighting("MyActionWrongClass.kt");
  }
}
