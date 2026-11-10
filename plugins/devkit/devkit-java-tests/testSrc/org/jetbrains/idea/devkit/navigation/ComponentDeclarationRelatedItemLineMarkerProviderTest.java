// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.devkit.core.icons.DevkitCoreIcons;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestDataPath("$CONTENT_ROOT/testData/navigation/componentDeclaration")
@TestApplication
public class ComponentDeclarationRelatedItemLineMarkerProviderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, DevkitJavaTestsUtil.TESTDATA_PATH + "navigation/componentDeclaration");

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package com.intellij.ui.components; public class JBList {}");
  }

  @Test
  public void testComponentSingleDeclaration() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.copyFileToProject("pluginComponentSingleDeclaration.xml");

    GutterMark gutter = myFixture.findGutter("MyComponent.java");
    ReadAction.runBlocking(() -> {
      DevKitGutterTargetsChecker.checkGutterTargets(gutter,
                                                    buildTooltipText("MyComponent"),
                                                    DevkitCoreIcons.Gutter.Plugin, "component");
    });
  }

  @Test
  public void testComponentMultipleDeclarations() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.copyFileToProject("pluginComponentMultipleDeclarations.xml");

    GutterMark gutter = myFixture.findGutter("MyComponent.java");
    ReadAction.runBlocking(() -> {
      DevKitGutterTargetsChecker.checkGutterTargets(gutter,
                                                    buildTooltipText("ActualImplementation", "MyComponent"),
                                                    DevkitCoreIcons.Gutter.Plugin, "component");
    });
  }

  @Test
  public void testComponentInterfaceMultipleDeclarations() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.copyFileToProject("pluginComponentInterfaceMultipleDeclarations.xml");

    GutterMark gutter = myFixture.findGutter("MyComponentInterface.java");
    ReadAction.runBlocking(() -> {
      DevKitGutterTargetsChecker.checkGutterTargets(gutter,
                                                    buildTooltipText("AnotherComponent", "MyComponent"),
                                                    DevkitCoreIcons.Gutter.Plugin, "component");
    });
  }

  private static String buildTooltipText(String... componentFqns) {
    return "<html><body>" +
           StringUtil.join(componentFqns, s -> s + "<br>", "") +
           "</body></html>";
  }
}
