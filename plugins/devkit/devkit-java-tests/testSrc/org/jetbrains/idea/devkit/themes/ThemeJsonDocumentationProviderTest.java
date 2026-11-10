// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.themes;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class ThemeJsonDocumentationProviderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testKeyWithDeprecation() {
    doTest("DebuggerTabs.selected<caret>Background",
           "<b>DebuggerTabs.selectedBackground</b>&nbsp;[IntelliJ Platform]<br/>" +
           "Background of the selected tab in the Debugger tool window. The foreground is EditorTabs.underlinedTabForeground.",
           "<div class=\"definition\"><pre><b>DebuggerTabs.selectedBackground</b><br/>[IntelliJ Platform] - [com.intellij]</pre></div>" +
           "<div class=\"content\"><font color=\"#ff0000\"><b>Deprecated</b></font><br/>" +
           "Background of the selected tab in the Debugger tool window. The foreground is EditorTabs.underlinedTabForeground.<br/><br/></div>" +
           "<table class=\"sections\"/>");
  }

  @Test
  public void testKeyWithSinceAndNoDescription() {
    doTest("DebuggerTabs.underline<caret>Height",
           "<b>DebuggerTabs.underlineHeight</b>&nbsp;[IntelliJ Platform]<br/>",
           "<div class=\"definition\"><pre><b>DebuggerTabs.underlineHeight</b><br/>[IntelliJ Platform] - [com.intellij]</pre></div>" +
           "<div class=\"content\">(no description)<br/><br/></div>" +
           "<table class=\"sections\"><tr><td class=\"section\" valign=\"top\"><p>Since:</p></td>" +
           "<td valign=\"top\">2019.2</td></tr></table>");
  }

  private void doTest(final String keyText, String quickNavigateInfo, String doc) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("my.theme.json", "{" +
                                               "\"ui\": {" +
                                               "  \"*\": {" +
                                               "    \"" + keyText + "\": \"#000080\"" +
                                               "   }" +
                                               "  }" +
                                               "}");
    ReadAction.runBlocking(() -> {
      final PsiElement docElement =
        DocumentationManager.getInstance(myFixture.getProject()).findTargetElement(myFixture.getEditor(),
                                                                         myFixture.getFile());
      DocumentationProvider provider = DocumentationManager.getProviderFromElement(docElement);

      assertEquals(quickNavigateInfo,
                   provider.getQuickNavigateInfo(docElement, getOriginalElement()));

      assertEquals(doc,
                   provider.generateDoc(docElement, getOriginalElement()));
    });
  }

  private PsiElement getOriginalElement() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
  }
}
