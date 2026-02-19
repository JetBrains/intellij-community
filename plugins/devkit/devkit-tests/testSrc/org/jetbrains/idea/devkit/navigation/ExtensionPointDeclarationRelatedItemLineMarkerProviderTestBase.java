// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.devkit.core.icons.DevkitCoreIcons;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.components.JBList;
import com.intellij.util.PathUtil;

public abstract class ExtensionPointDeclarationRelatedItemLineMarkerProviderTestBase extends JavaCodeInsightFixtureTestCase {

  protected abstract String getExtension();

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibrary("platform-extensions", PathUtil.getJarPathForClass(ExtensionPointName.class));
    moduleBuilder.addLibrary("platform-ide", PathUtil.getJarPathForClass(JBList.class));
    moduleBuilder.addLibrary("platform-core", PathUtil.getJarPathForClass(LanguageExtension.class));
  }

  public void testMyInterface() {
    assertSingleEPDeclaration("MyInterface" + getExtension(), "com.intellij.myInterfaceEP");
  }

  public void testMyClass() {
    assertSingleEPDeclaration("MyClass" + getExtension(), "com.intellij.myClassEP");
  }

  public void testMyWithImplementsInterface() {
    assertSingleEPDeclaration("MyWithImplementsInterface" + getExtension(), "com.intellij.myWithImplementsEP");
  }

  public void testMyBeanClassInterface() {
    // Negative test - should NOT show gutter for beanClass only
    myFixture.copyFileToProject("extensionPointDeclarationEPs.xml");

    final GutterMark gutter = myFixture.findGutter("MyBeanClassInterface" + getExtension());
    assertNull("Gutter should not appear for beanClass-only EP", gutter);
  }

  public void testMyMultipleEPsInterface() {
    // Should show gutter when multiple EPs reference the same interface
    VirtualFile pluginXmlFile = myFixture.copyFileToProject("extensionPointDeclarationEPs.xml");
    PsiFile pluginPsiFile = getPsiManager().findFile(pluginXmlFile);
    assertNotNull(pluginPsiFile);

    final GutterMark gutter = myFixture.findGutter("MyMultipleEPsInterface" + getExtension());
    assertNotNull(gutter);

    // Should list both EPs in tooltip
    DevKitGutterTargetsChecker.checkGutterTargets(gutter,
                                                  "<html><body>&nbsp;&nbsp;&nbsp;&nbsp;com.intellij.myMultipleEPs1<br>&nbsp;&nbsp;&nbsp;&nbsp;com.intellij.myMultipleEPs2<br></body></html>",
                                                  DevkitCoreIcons.Gutter.Plugin, "extensionPoint", "extensionPoint");
  }

  protected void assertSingleEPDeclaration(String filePath, String epFqn) {
    VirtualFile pluginXmlFile = myFixture.copyFileToProject("extensionPointDeclarationEPs.xml");
    PsiFile pluginPsiFile = getPsiManager().findFile(pluginXmlFile);
    assertNotNull(pluginPsiFile);

    int expectedTagPosition = pluginPsiFile.getText().indexOf("<extensionPoint name=\"" + StringUtil.substringAfterLast(epFqn, ".") + "\"");
    assertFalse(expectedTagPosition == -1);

    final GutterMark gutter = myFixture.findGutter(filePath);
    DevKitGutterTargetsChecker.checkGutterTargets(gutter, "<html><body>&nbsp;&nbsp;&nbsp;&nbsp;" + epFqn + "<br></body></html>",
                                                  DevkitCoreIcons.Gutter.Plugin, "extensionPoint");
  }
}
