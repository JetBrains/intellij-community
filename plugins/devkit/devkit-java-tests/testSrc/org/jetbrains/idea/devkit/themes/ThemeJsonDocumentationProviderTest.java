// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.themes;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class ThemeJsonDocumentationProviderTest extends LightJavaCodeInsightFixtureTestCase {

  public void testKeyWithDeprecation() {
    doTest("DebuggerTabs.selected<caret>Background",
           "<b>DebuggerTabs.selectedBackground</b>&nbsp;[IntelliJ Platform]<br/>" +
           "Background of the selected tab in the Debugger tool window. The foreground is EditorTabs.underlinedTabForeground.",
           "<div class=\"definition\"><pre><b>DebuggerTabs.selectedBackground</b><br/>[IntelliJ Platform] - [com.intellij]</pre></div>" +
           "<div class=\"content\"><font color=\"#ff0000\"><b>Deprecated</b></font><br/>" +
           "Background of the selected tab in the Debugger tool window. The foreground is EditorTabs.underlinedTabForeground.<br/><br/></div>" +
           "<table class=\"sections\"/>");
  }

  public void testKeyWithSinceAndNoDescription() {
    doTest("DebuggerTabs.underline<caret>Height",
           "<b>DebuggerTabs.underlineHeight</b>&nbsp;[IntelliJ Platform]<br/>",
           "<div class=\"definition\"><pre><b>DebuggerTabs.underlineHeight</b><br/>[IntelliJ Platform] - [com.intellij]</pre></div>" +
           "<div class=\"content\">(no description)<br/><br/></div>" +
           "<table class=\"sections\"><tr><td class=\"section\" valign=\"top\"><p>Since:</p></td>" +
           "<td valign=\"top\">2019.2</td></tr></table>");
  }

  private void doTest(final String keyText, String quickNavigateInfo, String doc) {
    myFixture.configureByText("my.theme.json", "{" +
                                               "\"ui\": {" +
                                               "  \"*\": {" +
                                               "    \"" + keyText + "\": \"#000080\"" +
                                               "   }" +
                                               "  }" +
                                               "}");
    final PsiElement docElement =
      DocumentationManager.getInstance(getProject()).findTargetElement(myFixture.getEditor(),
                                                                       myFixture.getFile());
    DocumentationProvider provider = DocumentationManager.getProviderFromElement(docElement);

    assertEquals(quickNavigateInfo,
                 provider.getQuickNavigateInfo(docElement, getOriginalElement()));

    assertEquals(doc,
                 provider.generateDoc(docElement, getOriginalElement()));
  }

  private PsiElement getOriginalElement() {
    return myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
  }
}
