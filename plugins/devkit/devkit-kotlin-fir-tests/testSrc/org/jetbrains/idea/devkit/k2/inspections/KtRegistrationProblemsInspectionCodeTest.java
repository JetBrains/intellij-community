// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.inspections.PluginModuleFixture;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;
import org.junit.jupiter.api.Test;

import static org.jetbrains.idea.devkit.inspections.PluginModuleFixtureKt.pluginModuleProjectFixture;
import static org.jetbrains.idea.devkit.inspections.RegistrationProblemsInspectionCodeFixtureKt.registrationProblemsInspectionCodeFixture;

@TestDataPath("$CONTENT_ROOT/testData/inspections/registrationProblems/code")
@TestApplication
public class KtRegistrationProblemsInspectionCodeTest {
  private static final TestFixture<LightJavaProject> projectFixture = pluginModuleProjectFixture();
  private final TestFixture<PluginModuleFixture> registrationProblems =
    registrationProblemsInspectionCodeFixture(projectFixture, DevkitKtTestsUtil.TESTDATA_PATH + "inspections/registrationProblems/code");

  @Test
  public void testComponentAbstractImplementation() {
    LightJavaCodeInsightTestFixture myFixture = registrationProblems.get().getFixture();
    registrationProblems.get().setPluginXml("ComponentAbstractImplementation.xml");
    myFixture.testHighlighting("AbstractApplicationComponent.kt");
  }

  @Test
  public void testApplicationComponentMustNotInherit() {
    LightJavaCodeInsightTestFixture myFixture = registrationProblems.get().getFixture();
    registrationProblems.get().setPluginXml("ApplicationComponentMustNotInherit.xml");
    myFixture.testHighlighting("MyApplicationComponentMustNotInherit.kt");
  }

  @Test
  public void testActionAbstractClass() {
    LightJavaCodeInsightTestFixture myFixture = registrationProblems.get().getFixture();
    registrationProblems.get().setPluginXml("ActionAbstractClass.xml");
    myFixture.testHighlighting("MyAbstractAction.kt");
  }

  @Test
  public void testActionWithoutDefaultCtor() {
    LightJavaCodeInsightTestFixture myFixture = registrationProblems.get().getFixture();
    registrationProblems.get().setPluginXml("ActionWithoutDefaultCtor.xml");
    myFixture.testHighlighting("MyActionWithoutDefaultCtor.kt");
  }

  @Test
  public void testActionWrongClass() {
    LightJavaCodeInsightTestFixture myFixture = registrationProblems.get().getFixture();
    registrationProblems.get().setPluginXml("ActionWrongClass.xml");
    myFixture.testHighlighting("MyActionWrongClass.kt");
  }
}
