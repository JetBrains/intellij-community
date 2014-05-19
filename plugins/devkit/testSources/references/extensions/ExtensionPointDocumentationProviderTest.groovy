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
package org.jetbrains.idea.devkit.references.extensions
import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.openapi.application.PluginPathManager
import com.intellij.pom.PomTargetPsiElement
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.ui.UIUtil
import com.intellij.util.xml.DomTarget

class ExtensionPointDocumentationProviderTest extends LightCodeInsightFixtureTestCase {

  @Override
  String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/references/extensions"
  }

  void testExtensionPointDocumentation() {
    myFixture.configureByFiles("extensionPointDocumentation.xml",
                               "bar/MyExtensionPoint.java")

    PsiElement originalElement = myFixture.getFile().findElementAt(myFixture.getCaretOffset())
    PomTargetPsiElement pomTargetPsiElement = assertInstanceOf(myFixture.getElementAtCaret(), PomTargetPsiElement.class)
    DomTarget domTarget = assertInstanceOf(pomTargetPsiElement.getTarget(), DomTarget.class)
    PsiElement epPsiElement = domTarget.getNavigationElement()

    DocumentationProvider provider = DocumentationManager.getProviderFromElement(epPsiElement)
    String docBody = UIUtil.getHtmlBody(provider.generateDoc(epPsiElement, originalElement))
    assertEquals("""<small><b>bar</b></small><PRE>public interface <b>MyExtensionPoint</b></PRE>
   MyExtensionPoint JavaDoc.""", docBody)

    assertEquals("""[$myModule.name] foo
<b>bar</b> [extensionPointDocumentation.xml]
bar.MyExtensionPoint""", provider.getQuickNavigateInfo(epPsiElement, originalElement))
  }
}