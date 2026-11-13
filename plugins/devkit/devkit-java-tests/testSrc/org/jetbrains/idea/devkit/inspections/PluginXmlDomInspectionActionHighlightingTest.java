// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.idea.devkit.inspections.PluginXmlDomInspectionFixtureKt.pluginXmlDomInspectionFixture;

@TestDataPath("$CONTENT_ROOT/testData/inspections/registrationProblems/xml/actions")
@TestApplication
public class PluginXmlDomInspectionActionHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<PluginXmlDomInspectionFixture> pluginXml =
    pluginXmlDomInspectionFixture(projectFixture, DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/registrationProblems/xml/actions");

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = pluginXml.get().getFixture();

    pluginXml.get().setUpActionClasses(!myFixture.getTestName(false).contains("Without"));
  }

  @Test
  public void testActionAbstractClass() {
    LightJavaCodeInsightTestFixture myFixture = pluginXml.get().getFixture();
    myFixture.testHighlighting("ActionAbstractClass.xml",
                               "MyAbstractAction.java");
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
                               "MyActionWithoutDefaultCtor.java");
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
    myFixture.addClass("package foo.bar; public class BarAction extends com.intellij.openapi.actionSystem.AnAction {}");
    myFixture.addClass("""
                       package foo;
                       class PackagePrivateActionBase extends com.intellij.openapi.actionSystem.AnAction {
                         PackagePrivateActionBase() {}
                       }""");
    myFixture.addClass("package foo; public class ActionWithDefaultConstructor extends PackagePrivateActionBase {}");
    myFixture.addClass("package foo.bar; class BarGroup extends com.intellij.openapi.actionSystem.ActionGroup {}");
    myFixture.addClass("""
                         package foo.bar;
                         import org.jetbrains.annotations.NotNull;
                         public class GroupWithCanBePerformed extends com.intellij.openapi.actionSystem.ActionGroup {
                           @Override
                           public boolean canBePerformed(@NotNull com.intellij.openapi.actionSystem.DataContext context) { return true; }
                         }""");

    // fake Executor
    myFixture.addClass("""
    package com.intellij.execution;
      public abstract class Executor {
        public abstract String getId();
        public abstract String getContextActionId();
      }
    """);
    myFixture.addClass("""
    public class MyExecutor extends com.intellij.execution.Executor {
      @Override
      @org.jetbrains.annotations.NotNull
      public@NotNull String getId() {
        return "MyExecutorId";
      }
      @Override
      public String getContextActionId() {
        return "MyExecutorContextActionId";
      }
    }
    """);
    // toolwindow ID
    myFixture.addClass("""
    package com.intellij.openapi.wm;
    interface ToolWindowId {
      String FAVORITES = "ToolWindowIdFromConstants";
    }
    """);
    myFixture.addClass("""
    package com.intellij.ui.components;
    public class JBList {}
    """);

    myFixture.addFileToProject("keymaps/MyKeymap.xml", "<keymap name=\"MyKeymap\"/>");
    myFixture.testHighlighting("ActionComplexHighlighting.xml");
  }

  @Test
  public void testActionGroupWithoutCanBePerformed() {
    LightJavaCodeInsightTestFixture myFixture = pluginXml.get().getFixture();
    myFixture.addClass("package foo.bar; public class BarAction extends com.intellij.openapi.actionSystem.AnAction {}");
    myFixture.addClass("""
                         package foo.bar;

                         public class GroupWithoutCanBePerformed extends com.intellij.openapi.actionSystem.ActionGroup {
                         }
                         """);

    myFixture.testHighlighting("ActionGroupWithoutCanBePerformed.xml");
  }

  @Test
  public void testGroupKeepContent() {
    LightJavaCodeInsightTestFixture myFixture = pluginXml.get().getFixture();
    myFixture.addClass("package foo.bar; public class BarAction extends com.intellij.openapi.actionSystem.AnAction {}");

    myFixture.testHighlighting("GroupKeepContent.xml");
  }

  @Test
  public void testReferenceWithoutEffect() {
    LightJavaCodeInsightTestFixture myFixture = pluginXml.get().getFixture();
    myFixture.addClass("package foo.bar; public class BarAction extends com.intellij.openapi.actionSystem.AnAction {}");

    myFixture.testHighlighting("ReferenceWithoutEffect.xml");
  }
}
