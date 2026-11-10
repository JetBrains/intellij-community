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

@SuppressWarnings("NewClassNamingConvention")
@TestDataPath("$CONTENT_ROOT/testData/navigation/listenerDeclaration")
@TestApplication
public class ListenerPluginDescriptorDeclarationRelatedItemLineMarkerProviderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, DevkitJavaTestsUtil.TESTDATA_PATH + "navigation/listenerDeclaration");

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package com.intellij.ui.components; public class JBList {}");
  }

  @Test
  public void testListenerSameTopic() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.copyFileToProject("pluginListener.xml");

    GutterMark gutter = myFixture.findGutter("MyListener.java");
    ReadAction.runBlocking(() -> {
      DevKitGutterTargetsChecker.checkGutterTargets(gutter,
                                                    buildTooltipText("MyListenerTopic"),
                                                    DevkitCoreIcons.Gutter.Plugin, "listener");
    });
  }

  @Test
  public void testListenerMultipleTopics() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.copyFileToProject("pluginListenerMultipleTopics.xml");

    GutterMark gutter = myFixture.findGutter("MyListener.java");
    ReadAction.runBlocking(() -> {
      DevKitGutterTargetsChecker.checkGutterTargets(gutter,
                                                    buildTooltipText("AnotherTopic", "MyListenerTopic", "YetAnotherTopic"),
                                                    DevkitCoreIcons.Gutter.Plugin, "listener");
    });
  }

  @Test
  public void testTopicMultipleListeners() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.copyFileToProject("pluginTopicMultipleListener.xml");

    GutterMark gutter = myFixture.findGutter("MyTopic.java");
    ReadAction.runBlocking(() -> {
      DevKitGutterTargetsChecker.checkGutterTargets(gutter,
                                                    buildTooltipText("AnotherListener", "MyListener"),
                                                    DevkitCoreIcons.Gutter.Plugin, "listener");
    });
  }

  private static String buildTooltipText(String... topics) {
    return "<html><body>" +
           StringUtil.join(topics, s -> s + "<br>", "") +
           "</body></html>";
  }
}
