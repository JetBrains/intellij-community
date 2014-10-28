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

    assertEquals("<em>EP Definition</em><br/>" +
                 epDefinition +
                 "<br/><br/>" +
                 "<em>EP Implementation</em>" +
                 "<html><head>    <style type=\"text/css\">        #error {            background-color: #eeeeee;            margin-bottom: 10px;        }        p {            margin: 5px 0;        }    </style></head>" +
                 "<body><small><b>bar</b></small><PRE>public interface <b>MyExtensionPoint</b></PRE>\n" +
                 "   MyExtensionPoint JavaDoc.</body></html>",
                 provider.generateDoc(docElement, null));
  }
}
