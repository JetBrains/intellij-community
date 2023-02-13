// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/registrationProblems/xml/actions")
public class PluginXmlDomInspectionActionHighlightingTest extends PluginXmlDomInspectionTestBase {
  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/registrationProblems/xml/actions";
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

  @SuppressWarnings({"ComponentNotRegistered", "removal"})
  public void testActionComplexHighlighting() {
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

    myFixture.addFileToProject("keymaps/MyKeymap.xml", "<keymap/>");
    myFixture.testHighlighting("ActionComplexHighlighting.xml");
  }
}
