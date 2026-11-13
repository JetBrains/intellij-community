// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.Language;
import org.jetbrains.idea.devkit.inspections.PluginXmlDomInspectionFixture;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.idea.devkit.inspections.PluginXmlDomInspectionFixtureKt.pluginXmlDomInspectionFixture;

@TestDataPath("$CONTENT_ROOT/testData/inspections/registrationProblems/xml/actions")
@TestApplication
public class KtPluginXmlDomInspectionActionHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<PluginXmlDomInspectionFixture> pluginXml =
    pluginXmlDomInspectionFixture(projectFixture, DevkitKtTestsUtil.TESTDATA_PATH + "inspections/registrationProblems/xml/actions");

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = pluginXml.get().getFixture();

    pluginXml.get().setUpActionClasses(!myFixture.getTestName(false).contains("Without"));
  }

  @Test
  public void testActionAbstractClass() {
    LightJavaCodeInsightTestFixture myFixture = pluginXml.get().getFixture();
    myFixture.testHighlighting("ActionAbstractClass.xml",
                               "MyAbstractAction.kt");
  }

  @Test
  public void testActionUnresolvedClass() {
    LightJavaCodeInsightTestFixture myFixture = pluginXml.get().getFixture();
    myFixture.testHighlighting("ActionUnresolvedClass.xml");
  }

  @Test
  public void testActionWithoutDefaultCtor() {
    LightJavaCodeInsightTestFixture myFixture = pluginXml.get().getFixture();
    myFixture.testHighlighting("ActionWithoutDefaultCtor.xml",
                               "MyActionWithoutDefaultCtor.kt");
  }

  @Test
  public void testActionWrongClass() {
    LightJavaCodeInsightTestFixture myFixture = pluginXml.get().getFixture();
    myFixture.testHighlighting("ActionWrongClass.xml");
  }

  @Test
  public void testActionComplexHighlighting() {
    LightJavaCodeInsightTestFixture myFixture = pluginXml.get().getFixture();
    myFixture.copyFileToProject("MyBundle.properties");
    myFixture.copyFileToProject("AnotherBundle.properties");
    addKotlinClass("foo/bar/BarAction.kt",
                   """
                     package foo.bar
                     class BarAction : com.intellij.openapi.actionSystem.AnAction() {}""");
    addKotlinClass("foo/InternalActionBase.kt", """
      package foo
      internal class InternalActionBase : com.intellij.openapi.actionSystem.AnAction() {
        constructor() {}
      }""");
    addKotlinClass("foo/ActionWithDefaultConstructor.kt", """
      package foo
      class ActionWithDefaultConstructor : InternalActionBase() {}""");
    addKotlinClass("foo/bar/BarGroup.kt", """
      package foo.bar
      public class BarGroup : com.intellij.openapi.actionSystem.ActionGroup() {}""");
    addKotlinClass("foo/bar/GroupWithCanBePerformed.kt", """
      package foo.bar
      public class GroupWithCanBePerformed : com.intellij.openapi.actionSystem.ActionGroup() {
        override fun canBePerformed(context: com.intellij.openapi.actionSystem.DataContext): Boolean {
          return true
        }
      }""");

    myFixture.addClass("""
                         package com.intellij.ui.components;
                         public class JBList {}
                         """);
    myFixture.addFileToProject("keymaps/MyKeymap.xml", "<keymap name=\"MyKeymap\"/>");
    myFixture.testHighlighting("ActionComplexHighlighting.xml");
  }

  private void addKotlinClass(String fileName, @Language("kotlin") String code) {
    LightJavaCodeInsightTestFixture myFixture = pluginXml.get().getFixture();
    myFixture.addFileToProject(fileName, code);
  }
}
