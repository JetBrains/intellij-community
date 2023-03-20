// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.navigation.DomGotoRelatedItem;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.icons.AllIcons;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.components.JBList;
import com.intellij.util.PathUtil;
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
    moduleBuilder.addLibrary("platform-editor", PathUtil.getJarPathForClass(AnAction.class));
    moduleBuilder.addLibrary("platform-ide", PathUtil.getJarPathForClass(JBList.class));
    moduleBuilder.addLibrary("platform-util-ui", PathUtil.getJarPathForClass(AllIcons.class));
  }

  public void testActionSingleDeclaration() {
    myFixture.copyFileToProject("pluginActionSingleDeclaration.xml");

    GutterMark gutter = myFixture.findGutter("MyAction.java");
    DevKitGutterTargetsChecker.checkGutterTargets(gutter,
                                                  buildTooltipText("singleDeclaration") ,
                                                  DevKitIcons.Gutter.Plugin, "action");
  }

  public void testActionMultipleDeclarations() {
    myFixture.copyFileToProject("pluginActionMultipleDeclarations.xml");

    GutterMark gutter = myFixture.findGutter("MyAction.java");
    DevKitGutterTargetsChecker.checkGutterTargets(gutter,
                                                  buildTooltipText("firstDeclaration", "secondDeclaration"),
                                                  DevKitIcons.Gutter.Plugin, "action");

    List<GotoRelatedItem> relatedItems = NavigationUtil.collectRelatedItems(myFixture.findClass("MyAction"), null);
    assertSize(2, relatedItems);
    DomGotoRelatedItem first = assertInstanceOf(relatedItems.get(0), DomGotoRelatedItem.class);
    assertEquals("DevKit", first.getGroup());
    assertEquals("firstDeclaration", first.getCustomName());
    assertEquals(AllIcons.Actions.AddFile, first.getCustomIcon());
  }

  public void testActionGroupMultipleDeclarations() {
     myFixture.copyFileToProject("pluginActionGroupMultipleDeclarations.xml");

    GutterMark gutter = myFixture.findGutter("MyActionGroup.java");
    DevKitGutterTargetsChecker.checkGutterTargets(gutter,
                                                  buildTooltipText("firstDeclaration", "secondDeclaration"),
                                                  DevKitIcons.Gutter.Plugin, "group");
  }

  private static String buildTooltipText(String... actionIds) {
    return "<html><body>" +
           StringUtil.join(actionIds, s -> "&nbsp;&nbsp;&nbsp;&nbsp;" + s + "<br>", "") +
           "</body></html>";
  }
}
