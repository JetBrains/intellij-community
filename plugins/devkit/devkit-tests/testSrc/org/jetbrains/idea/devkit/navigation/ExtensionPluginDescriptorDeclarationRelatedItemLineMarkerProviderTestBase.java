// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitIcons;

public abstract class ExtensionPluginDescriptorDeclarationRelatedItemLineMarkerProviderTestBase
  extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package com.intellij.ui.components; public class JBList {}");
  }

  protected void doTestExtension(@NotNull String file, @NotNull String xmlDeclarationText) {
    VirtualFile pluginXmlFile = myFixture.copyFileToProject("plugin.xml");
    PsiFile pluginPsiFile = getPsiManager().findFile(pluginXmlFile);
    assertNotNull(pluginPsiFile);

    int expectedTagPosition = pluginPsiFile.getText().indexOf(xmlDeclarationText);
    assertFalse(expectedTagPosition == -1);

    GutterMark gutter = myFixture.findGutter(file);
    DevKitGutterTargetsChecker.checkGutterTargets(gutter, "<html><body>&nbsp;&nbsp;&nbsp;&nbsp;com.intellij.myEp<br></body></html>",
                                                  DevKitIcons.Gutter.Plugin, "myEp");
  }

  protected void doTestInvalidExtension(@NotNull String file) {
    myFixture.configureByFile("plugin.xml");
    assertNull(myFixture.findGutter(file));
  }
}
