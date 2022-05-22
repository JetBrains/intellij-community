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
    VirtualFile pluginXmlFile = myFixture.copyFileToProject("pluginListener.xml");

    GutterMark gutter = myFixture.findGutter("MyListener.java");
    DevKitGutterTargetsChecker.checkGutterTargets(gutter,
                                                  "<html><body>" +
                                                  buildTooltipText(pluginXmlFile, 151, "MyListenerTopic") +
                                                  buildTooltipText(pluginXmlFile, 44, "MyListenerTopic") +
                                                  "</body></html>",
                                                  DevKitIcons.Gutter.Plugin, "listener");
  }

  public void testListenerMultipleTopics() {
    VirtualFile pluginXmlFile = myFixture.copyFileToProject("pluginListenerMultipleTopics.xml");

    GutterMark gutter = myFixture.findGutter("MyListener.java");
    DevKitGutterTargetsChecker.checkGutterTargets(gutter,
                                                  "<html><body>" +
                                                  buildTooltipText(pluginXmlFile, 151, "AnotherTopic") +
                                                  buildTooltipText(pluginXmlFile, 207, "YetAnotherTopic") +
                                                  buildTooltipText(pluginXmlFile, 44, "MyListenerTopic") +
                                                  "</body></html>",
                                                  DevKitIcons.Gutter.Plugin, "listener");
  }

  public void testTopicMultipleListeners() {
    VirtualFile pluginXmlFile = myFixture.copyFileToProject("pluginTopicMultipleListener.xml");

    GutterMark gutter = myFixture.findGutter("MyTopic.java");
    DevKitGutterTargetsChecker.checkGutterTargets(gutter,
                                                  "<html><body>" +
                                                  buildTooltipText(pluginXmlFile, 143, "AnotherListener") +
                                                  buildTooltipText(pluginXmlFile, 44, "MyListener") +
                                                  "</body></html>",
                                                  DevKitIcons.Gutter.Plugin, "listener");
  }

  private String buildTooltipText(VirtualFile pluginXmlFile, int expectedTagPosition, String topicFqn) {
    String pluginXmlPath = pluginXmlFile.getPath();

    String color = ColorUtil.toHex(UIUtil.getInactiveTextColor());
    return "&nbsp;&nbsp;&nbsp;&nbsp;<a href=\"#navigation/" + pluginXmlPath
           + ":" + expectedTagPosition + "\">" + topicFqn + "</a> listener in " + pluginXmlFile.getName() +
           " <font color=\"" + color + "\">[" + getModule().getName() + "]</font><br>";
  }
}
