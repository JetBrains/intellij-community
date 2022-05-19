// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.UIUtil;
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
    VirtualFile pluginXmlFile = myFixture.copyFileToProject("pluginComponentSingleDeclaration.xml");

    GutterMark gutter = myFixture.findGutter("MyComponent.java");
    DevKitGutterTargetsChecker.checkGutterTargets(gutter,
                                                  "<html><body>" +
                                                  buildTooltipText(pluginXmlFile, 45, "MyComponent") +
                                                  "</body></html>",
                                                  DevKitIcons.Gutter.Plugin, "component");
  }

  public void testComponentMultipleDeclarations() {
    VirtualFile pluginXmlFile = myFixture.copyFileToProject("pluginComponentMultipleDeclarations.xml");

    GutterMark gutter = myFixture.findGutter("MyComponent.java");
    DevKitGutterTargetsChecker.checkGutterTargets(gutter,
                                                  "<html><body>" +
                                                  buildTooltipText(pluginXmlFile, 141, "MyComponent") +
                                                  buildTooltipText(pluginXmlFile, 45, "MyComponent") +
                                                  "</body></html>",
                                                  DevKitIcons.Gutter.Plugin, "component");
  }

  public void testComponentInterfaceMultipleDeclarations() {
    VirtualFile pluginXmlFile = myFixture.copyFileToProject("pluginComponentInterfaceMultipleDeclarations.xml");

    GutterMark gutter = myFixture.findGutter("MyComponentInterface.java");
    DevKitGutterTargetsChecker.checkGutterTargets(gutter,
                                                  "<html><body>" +
                                                  buildTooltipText(pluginXmlFile, 208, "MyComponent") +
                                                  buildTooltipText(pluginXmlFile, 45, "AnotherComponent") +
                                                  "</body></html>",
                                                  DevKitIcons.Gutter.Plugin, "component");
  }

  private String buildTooltipText(VirtualFile pluginXmlFile, int expectedTagPosition, String componentFqn) {
    String pluginXmlPath = pluginXmlFile.getPath();

    String color = ColorUtil.toHex(UIUtil.getInactiveTextColor());
    return "&nbsp;&nbsp;&nbsp;&nbsp;<a href=\"#navigation/" + pluginXmlPath
           + ":" + expectedTagPosition + "\">" + componentFqn + "</a> component in " + pluginXmlFile.getName() +
           " <font color=\"" + color + "\">[" + getModule().getName() + "]</font><br>";
  }
}
