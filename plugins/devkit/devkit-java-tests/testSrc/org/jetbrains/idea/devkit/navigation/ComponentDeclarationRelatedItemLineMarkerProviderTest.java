// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.idea.devkit.DevKitIcons;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/navigation/componentDeclaration")
public class ComponentDeclarationRelatedItemLineMarkerProviderTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "navigation/componentDeclaration";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package com.intellij.ui.components; public class JBList {}");
  }

  public void testComponentSingleDeclaration() {
    myFixture.copyFileToProject("pluginComponentSingleDeclaration.xml");

    GutterMark gutter = myFixture.findGutter("MyComponent.java");
    DevKitGutterTargetsChecker.checkGutterTargets(gutter,
                                                  buildTooltipText("MyComponent"),
                                                  DevKitIcons.Gutter.Plugin, "component");
  }

  public void testComponentMultipleDeclarations() {
    myFixture.copyFileToProject("pluginComponentMultipleDeclarations.xml");

    GutterMark gutter = myFixture.findGutter("MyComponent.java");
    DevKitGutterTargetsChecker.checkGutterTargets(gutter,
                                                  buildTooltipText("ActualImplementation", "MyComponent"),
                                                  DevKitIcons.Gutter.Plugin, "component");
  }

  public void testComponentInterfaceMultipleDeclarations() {
    myFixture.copyFileToProject("pluginComponentInterfaceMultipleDeclarations.xml");

    GutterMark gutter = myFixture.findGutter("MyComponentInterface.java");
    DevKitGutterTargetsChecker.checkGutterTargets(gutter,
                                                  buildTooltipText("AnotherComponent", "MyComponent"),
                                                  DevKitIcons.Gutter.Plugin, "component");
  }

  private static String buildTooltipText(String... componentFqns) {
    return "<html><body>" +
           StringUtil.join(componentFqns, s -> "&nbsp;&nbsp;&nbsp;&nbsp;" + s + "<br>", "") +
           "</body></html>";
  }
}
