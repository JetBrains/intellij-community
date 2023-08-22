// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.idea.devkit.DevKitIcons;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@SuppressWarnings("NewClassNamingConvention")
@TestDataPath("$CONTENT_ROOT/testData/navigation/listenerDeclaration")
public class ListenerPluginDescriptorDeclarationRelatedItemLineMarkerProviderTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "navigation/listenerDeclaration";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package com.intellij.ui.components; public class JBList {}");
  }

  public void testListenerSameTopic() {
    myFixture.copyFileToProject("pluginListener.xml");

    GutterMark gutter = myFixture.findGutter("MyListener.java");
    DevKitGutterTargetsChecker.checkGutterTargets(gutter,
                                                  buildTooltipText("MyListenerTopic"),
                                                  DevKitIcons.Gutter.Plugin, "listener");
  }

  public void testListenerMultipleTopics() {
    myFixture.copyFileToProject("pluginListenerMultipleTopics.xml");

    GutterMark gutter = myFixture.findGutter("MyListener.java");
    DevKitGutterTargetsChecker.checkGutterTargets(gutter,
                                                  buildTooltipText("AnotherTopic", "MyListenerTopic", "YetAnotherTopic"),
                                                  DevKitIcons.Gutter.Plugin, "listener");
  }

  public void testTopicMultipleListeners() {
    myFixture.copyFileToProject("pluginTopicMultipleListener.xml");

    GutterMark gutter = myFixture.findGutter("MyTopic.java");
    DevKitGutterTargetsChecker.checkGutterTargets(gutter,
                                                  buildTooltipText("AnotherListener", "MyListener"),
                                                  DevKitIcons.Gutter.Plugin, "listener");
  }

  private static String buildTooltipText(String... topics) {
    return "<html><body>" +
           StringUtil.join(topics, s -> "&nbsp;&nbsp;&nbsp;&nbsp;" + s + "<br>", "") +
           "</body></html>";
  }
}
