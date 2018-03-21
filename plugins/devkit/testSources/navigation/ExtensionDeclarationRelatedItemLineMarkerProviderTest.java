/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

@TestDataPath("$CONTENT_ROOT/testData/navigation/extensionDeclaration")
public class ExtensionDeclarationRelatedItemLineMarkerProviderTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/navigation/extensionDeclaration";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    String extensionsJar = PathUtil.getJarPathForClass(ExtensionPointName.class);
    moduleBuilder.addLibrary("extensions", extensionsJar);
    String platformApiJar = PathUtil.getJarPathForClass(JBList.class);
    moduleBuilder.addLibrary("platform-api", platformApiJar);
  }


  public void testInvalidExtension() {
    doTestInvalidExtension("MyInvalidExtension.java");
  }

  public void testInvalidKtExtension() {
    doTestInvalidExtension("MyInvalidKtExtension.kt");
  }

  private void doTestInvalidExtension(String file) {
    myFixture.configureByFile("plugin.xml");
    assertNull(myFixture.findGutter(file));
  }


  public void testExtension() {
    doTestExtension("MyExtension.java", "<myEp implementation=\"MyExtension\"/>");
  }

  public void testKtExtension() {
    doTestExtension("MyKtExtension.kt", "<myEp implementation=\"MyKtExtension\"/>");
  }

  private void doTestExtension(String file, String xmlDeclarationText) {
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
}
