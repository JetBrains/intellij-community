/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.components.JBList;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.UIUtil;

@TestDataPath("$CONTENT_ROOT/testData/navigation/extensionPointDeclaration")
public class ExtensionPointDeclarationRelatedItemLineMarkerProviderTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/navigation/extensionPointDeclaration";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    String extensionsJar = PathUtil.getJarPathForClass(ExtensionPointName.class);
    moduleBuilder.addLibrary("extensions", extensionsJar);
    String platformApiJar = PathUtil.getJarPathForClass(JBList.class);
    moduleBuilder.addLibrary("platform-api", platformApiJar);
  }

  public void testMyStringEP() {
    assertSingleEPDeclaration("MyStringEP.java");
  }

  public void testMyStringEPViaConstant() {
    assertSingleEPDeclaration("MyStringEPViaConstant.java");
  }

  public void testMyStringEPConstructor() {
    assertSingleEPDeclaration("MyStringEPConstructor.java");
  }

  private void assertSingleEPDeclaration(String filePath) {
    PsiFile file = myFixture.configureByFile("plugin.xml");
    String path = file.getVirtualFile().getPath();
    Module module = ModuleUtilCore.findModuleForPsiElement(file);
    assertNotNull(module);
    String color = ColorUtil.toHex(UIUtil.getInactiveTextColor());
    int expectedTagPosition = file.getText().indexOf("<extensionPoint name=\"myStringEP\" interface=\"java.lang.String\"/>");
    String expectedTooltip = "<html><body>&nbsp;&nbsp;&nbsp;&nbsp;<a href=\"#navigation/" + path
                             + ":" + expectedTagPosition + "\">myStringEP</a> EP declaration in plugin.xml " +
                             "<font color=" + color + ">[" + module.getName() + "]</font><br></body></html>";

    final GutterMark gutter = myFixture.findGutter(filePath);
    DevKitGutterTargetsChecker.checkGutterTargets(gutter, expectedTooltip, AllIcons.Nodes.Plugin, "extensionPoint");
  }
}