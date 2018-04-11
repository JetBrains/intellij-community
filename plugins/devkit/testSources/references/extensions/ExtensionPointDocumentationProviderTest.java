package org.jetbrains.idea.devkit.references.extensions;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class ExtensionPointDocumentationProviderTest extends LightCodeInsightFixtureTestCase {
  @Override
  public String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/references/extensions";
  }

  public void testExtensionPointDocumentation() {
    myFixture.configureByFiles("extensionPointDocumentation.xml", "bar/MyExtensionPoint.java");

    final PsiElement docElement =
      DocumentationManager.getInstance(getProject()).findTargetElement(myFixture.getEditor(),
                                                                       myFixture.getFile());
    DocumentationProvider provider = DocumentationManager.getProviderFromElement(docElement);

    String epDefinition = "[" + myModule.getName() + "] foo<br/>" +
                          "<b>bar</b> (extensionPointDocumentation.xml)<br/>" +
                          "<a href=\"psi_element://bar.MyExtensionPoint\"><code>MyExtensionPoint</code></a>";

    assertEquals(epDefinition,
                 provider.getQuickNavigateInfo(docElement, null));

    assertEquals("<em>EP Definition</em><br/>[light_idea_test_case] foo<br/><b>bar</b> (extensionPointDocumentation.xml)<br/><a href=\"psi_element://bar.MyExtensionPoint\"><code>MyExtensionPoint</code></a><br/><br/><em>EP Implementation</em><div class='definition'><pre>bar<br>public interface <b>MyExtensionPoint</b></pre></div><div class='content'>\n" +
                 "   MyExtensionPoint JavaDoc.\n" +
                 " </div><table class='sections'><p></table>",
                 provider.generateDoc(docElement, null));
  }
}
