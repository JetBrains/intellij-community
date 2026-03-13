// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections;

import com.intellij.testFramework.TestDataPath;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.PluginXmlDomInspectionTestBase;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode;
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider;
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProviderKt;

@TestDataPath("$CONTENT_ROOT/testData/inspections/registrationProblems/xml/actions")
public class KtPluginXmlDomInspectionActionHighlightingTest extends PluginXmlDomInspectionTestBase implements ExpectedPluginModeProvider {
  @Override
  public @NotNull KotlinPluginMode getPluginMode() {
    return KotlinPluginMode.K2;
  }

  @Override
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/registrationProblems/xml/actions";
  }

  @Override
  protected void setUp() throws Exception {
    ExpectedPluginModeProviderKt.setUpWithKotlinPlugin(this, getTestRootDisposable(), () -> super.setUp());

    setUpActionClasses(!getTestName(false).contains("Without"));
  }

  public void testActionAbstractClass() {
    myFixture.testHighlighting("ActionAbstractClass.xml",
                               "MyAbstractAction.kt");
  }

  public void testActionUnresolvedClass() {
    myFixture.testHighlighting("ActionUnresolvedClass.xml");
  }

  public void testActionWithoutDefaultCtor() {
    myFixture.testHighlighting("ActionWithoutDefaultCtor.xml",
                               "MyActionWithoutDefaultCtor.kt");
  }

  public void testActionWrongClass() {
    myFixture.testHighlighting("ActionWrongClass.xml");
  }

  public void testActionComplexHighlighting() {
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
    myFixture.addFileToProject(fileName, code);
  }
}
