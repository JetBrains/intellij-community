// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.components.JBList;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

public abstract class ExtensionDeclarationRelatedItemLineMarkerProviderTestBase extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    String extensionsJar = PathUtil.getJarPathForClass(ExtensionPointName.class);
    moduleBuilder.addLibrary("extensions", extensionsJar);
    String platformApiJar = PathUtil.getJarPathForClass(JBList.class);
    moduleBuilder.addLibrary("platform-api", platformApiJar);
  }

  protected void doTestExtension(@NotNull String file, @NotNull String xmlDeclarationText) {
    PsiFile pluginXmlFile = myFixture.configureByFile("plugin.xml");
    String pluginXmlPath = pluginXmlFile.getVirtualFile().getPath();

    Module module = ModuleUtilCore.findModuleForPsiElement(pluginXmlFile);
    assertNotNull(module);

    String color = ColorUtil.toHex(UIUtil.getInactiveTextColor());
    int expectedTagPosition = pluginXmlFile.getText().indexOf(xmlDeclarationText);
    String expectedTooltip = "<html><body>&nbsp;&nbsp;&nbsp;&nbsp;<a href=\"#navigation/" + pluginXmlPath
                             + ":" + expectedTagPosition + "\">myEp</a> declaration in plugin.xml " +
                             "<font color=" + color + ">[" + module.getName() + "]</font><br></body></html>";

    GutterMark gutter = myFixture.findGutter(file);
    DevKitGutterTargetsChecker.checkGutterTargets(gutter, expectedTooltip, AllIcons.Nodes.Plugin, "myEp");
  }

  protected void doTestInvalidExtension(@NotNull String file) {
    myFixture.configureByFile("plugin.xml");
    assertNull(myFixture.findGutter(file));
  }
}
