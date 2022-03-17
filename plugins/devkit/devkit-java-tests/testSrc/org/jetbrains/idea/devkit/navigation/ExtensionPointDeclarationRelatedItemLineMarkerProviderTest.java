// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.components.JBList;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.idea.devkit.DevKitIcons;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/navigation/extensionPointDeclaration")
public class ExtensionPointDeclarationRelatedItemLineMarkerProviderTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "navigation/extensionPointDeclaration";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    String extensionsJar = PathUtil.getJarPathForClass(ExtensionPointName.class);
    moduleBuilder.addLibrary("extensions", extensionsJar);
    String platformApiJar = PathUtil.getJarPathForClass(JBList.class);
    moduleBuilder.addLibrary("platform-api", platformApiJar);
  }

  public void testMyStringEP() {
    assertStringEP("MyStringEP.java");
  }

  public void testMyStringEPViaConstant() {
    assertStringEP("MyStringEPViaConstant.java");
  }

  public void testMyStringEPConstructor() {
    assertStringEP("MyStringEPConstructor.java");
  }

  public void testMyStringProjectEP() {
    assertStringEP("MyStringProjectEP.java");
  }

  public void testMyStringKeyedLazyInstanceEP() {
    assertStringEP("MyStringKeyedLazyInstanceEP.java");
  }

  public void testMyBeanClassStringEP() {
    assertSingleEPDeclaration("MyBeanClassStringEP.java", "com.intellij.myBeanClassStringEP");
  }

  private void assertStringEP(String filePath) {
    assertSingleEPDeclaration(filePath, "com.intellij.myStringEP");
  }

  private void assertSingleEPDeclaration(String filePath, String epFqn) {
    PsiFile file = myFixture.configureByFile("plugin.xml");
    String path = file.getVirtualFile().getPath();
    Module module = ModuleUtilCore.findModuleForPsiElement(file);
    assertNotNull(module);
    String color = ColorUtil.toHex(UIUtil.getInactiveTextColor());
    int expectedTagPosition = file.getText().indexOf("<extensionPoint name=\"" + StringUtil.substringAfterLast(epFqn, ".") + "\"");
    String expectedTooltip = "<html><body>&nbsp;&nbsp;&nbsp;&nbsp;<a href=\"#navigation/" + path
                             + ":" + expectedTagPosition + "\">" + epFqn + "</a> extension point declaration in plugin.xml " +
                             "<font color=\"" + color + "\">[" + module.getName() + "]</font><br></body></html>";

    final GutterMark gutter = myFixture.findGutter(filePath);
    DevKitGutterTargetsChecker.checkGutterTargets(gutter, expectedTooltip, DevKitIcons.Gutter.Plugin, "extensionPoint");
  }
}