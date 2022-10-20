// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.components.JBList;
import com.intellij.util.PathUtil;
import org.jetbrains.idea.devkit.DevKitIcons;

public abstract class ExtensionPointDeclarationRelatedItemLineMarkerProviderTestBase extends JavaCodeInsightFixtureTestCase {

  protected abstract String getExtension();

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibrary("extensions", PathUtil.getJarPathForClass(ExtensionPointName.class));
    moduleBuilder.addLibrary("platform-api", PathUtil.getJarPathForClass(JBList.class));
    moduleBuilder.addLibrary("platform-core", PathUtil.getJarPathForClass(LanguageExtension.class));
  }

  protected void assertStringEP() {
    assertSingleEPDeclaration(getTestName(false) + getExtension(), "com.intellij.myStringEP");
  }

  protected void assertSingleEPDeclaration(String filePath, String epFqn) {
    VirtualFile pluginXmlFile = myFixture.copyFileToProject("extensionPointDeclarationEPs.xml");
    PsiFile pluginPsiFile = getPsiManager().findFile(pluginXmlFile);
    assertNotNull(pluginPsiFile);

    int expectedTagPosition = pluginPsiFile.getText().indexOf("<extensionPoint name=\"" + StringUtil.substringAfterLast(epFqn, ".") + "\"");
    assertFalse(expectedTagPosition == -1);

    final GutterMark gutter = myFixture.findGutter(filePath);
    DevKitGutterTargetsChecker.checkGutterTargets(gutter, "<html><body>&nbsp;&nbsp;&nbsp;&nbsp;" + epFqn + "<br></body></html>",
                                                  DevKitIcons.Gutter.Plugin, "extensionPoint");
  }
}
