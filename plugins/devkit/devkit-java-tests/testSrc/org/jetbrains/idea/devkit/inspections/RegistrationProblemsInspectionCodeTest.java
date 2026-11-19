// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.junit.jupiter.api.Test;

import static org.jetbrains.idea.devkit.inspections.PluginModuleFixtureKt.pluginModuleProjectFixture;
import static org.jetbrains.idea.devkit.inspections.RegistrationProblemsInspectionCodeFixtureKt.registrationProblemsInspectionCodeFixture;

@TestDataPath("$CONTENT_ROOT/testData/inspections/registrationProblems/code")
@TestApplication
public class RegistrationProblemsInspectionCodeTest {
  private static final TestFixture<LightJavaProject> projectFixture = pluginModuleProjectFixture();
  private final TestFixture<PluginModuleFixture> registrationProblems =
    registrationProblemsInspectionCodeFixture(projectFixture, DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/registrationProblems/code");

  @Test
  public void testComponentAbstractImplementation() {
    LightJavaCodeInsightTestFixture myFixture = registrationProblems.get().getFixture();
    registrationProblems.get().setPluginXml("ComponentAbstractImplementation.xml");
    myFixture.testHighlighting("AbstractApplicationComponent.java");
  }

  @Test
  public void testApplicationComponentMustNotInherit() {
    LightJavaCodeInsightTestFixture myFixture = registrationProblems.get().getFixture();
    registrationProblems.get().setPluginXml("ApplicationComponentMustNotInherit.xml");
    myFixture.testHighlighting("MyApplicationComponentMustNotInherit.java");
  }

  @Test
  public void testActionAbstractClass() {
    LightJavaCodeInsightTestFixture myFixture = registrationProblems.get().getFixture();
    registrationProblems.get().setPluginXml("ActionAbstractClass.xml");
    myFixture.testHighlighting("MyAbstractAction.java");
  }

  @Test
  public void testActionWithoutDefaultCtor() {
    LightJavaCodeInsightTestFixture myFixture = registrationProblems.get().getFixture();
    registrationProblems.get().setPluginXml("ActionWithoutDefaultCtor.xml");
    myFixture.testHighlighting("MyActionWithoutDefaultCtor.java");
  }

  @Test
  public void testActionWrongClass() {
    LightJavaCodeInsightTestFixture myFixture = registrationProblems.get().getFixture();
    registrationProblems.get().setPluginXml("ActionWrongClass.xml");
    myFixture.testHighlighting("MyActionWrongClass.java");
  }
}
