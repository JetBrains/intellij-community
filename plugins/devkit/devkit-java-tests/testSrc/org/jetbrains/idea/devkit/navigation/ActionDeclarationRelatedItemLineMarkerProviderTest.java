// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.navigation.DomGotoRelatedItem;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.icons.AllIcons;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.components.JBList;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.idea.devkit.DevKitIcons;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

import java.util.List;

@TestDataPath("$CONTENT_ROOT/testData/navigation/actionDeclaration")
public class ActionDeclarationRelatedItemLineMarkerProviderTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "navigation/actionDeclaration";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    String platformEditorJar = PathUtil.getJarPathForClass(AnAction.class);
    moduleBuilder.addLibrary("platform-editor", platformEditorJar);
    String platformIdeJar = PathUtil.getJarPathForClass(JBList.class);
    moduleBuilder.addLibrary("platform-ide", platformIdeJar);
    String platformUtilJar = PathUtil.getJarPathForClass(AllIcons.class);
    moduleBuilder.addLibrary("platform-util", platformUtilJar);
  }

  public void testActionSingleDeclaration() {
    VirtualFile pluginXmlFile = myFixture.copyFileToProject("pluginActionSingleDeclaration.xml");

    GutterMark gutter = myFixture.findGutter("MyAction.java");
    DevKitGutterTargetsChecker.checkGutterTargets(gutter,
                                                  "<html><body>" +
                                                  buildTooltipText(pluginXmlFile, 30, "singleDeclaration", "action") +
                                                  "</body></html>",
                                                  DevKitIcons.Gutter.Plugin, "action");
  }

  public void testActionMultipleDeclarations() {
    VirtualFile pluginXmlFile = myFixture.copyFileToProject("pluginActionMultipleDeclarations.xml");

    GutterMark gutter = myFixture.findGutter("MyAction.java");
    DevKitGutterTargetsChecker.checkGutterTargets(gutter,
                                                  "<html><body>" +
                                                  buildTooltipText(pluginXmlFile, 140, "secondDeclaration", "action") +
                                                  buildTooltipText(pluginXmlFile, 30, "firstDeclaration", "action") +
                                                  "</body></html>",
                                                  DevKitIcons.Gutter.Plugin, "action");

    List<GotoRelatedItem> relatedItems = NavigationUtil.collectRelatedItems(myFixture.findClass("MyAction"), null);
    assertSize(2, relatedItems);
    DomGotoRelatedItem first = assertInstanceOf(relatedItems.get(0), DomGotoRelatedItem.class);
    assertEquals("DevKit", first.getGroup());
    assertEquals("firstDeclaration", first.getCustomName());
    assertEquals(AllIcons.Actions.AddFile, first.getCustomIcon());
  }

  public void testActionGroupMultipleDeclarations() {
    VirtualFile pluginXmlFile = myFixture.copyFileToProject("pluginActionGroupMultipleDeclarations.xml");

    GutterMark gutter = myFixture.findGutter("MyActionGroup.java");
    DevKitGutterTargetsChecker.checkGutterTargets(gutter,
                                                  "<html><body>" +
                                                  buildTooltipText(pluginXmlFile, 112, "secondDeclaration", "action group") +
                                                  buildTooltipText(pluginXmlFile, 30, "firstDeclaration", "action group") +
                                                  "</body></html>",
                                                  DevKitIcons.Gutter.Plugin, "group");
  }

  private String buildTooltipText(VirtualFile pluginXmlFile, int expectedTagPosition, String actionId, String actionType) {
    String pluginXmlPath = pluginXmlFile.getPath();

    String color = ColorUtil.toHex(UIUtil.getInactiveTextColor());
    return "&nbsp;&nbsp;&nbsp;&nbsp;<a href=\"#navigation/" + pluginXmlPath
           + ":" + expectedTagPosition + "\">" + actionId + "</a> " + actionType + " in " + pluginXmlFile.getName() +
           " <font color=\"" + color + "\">[" + getModule().getName() + "]</font><br>";
  }
}
