package org.jetbrains.idea.devkit.references.extensions;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.xml.DomTarget;

public class ExtensionPointDocumentationProviderTest extends LightCodeInsightFixtureTestCase {
  @Override
  public String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/references/extensions";
  }

  public void testExtensionPointDocumentation() {
    myFixture.configureByFiles("extensionPointDocumentation.xml", "bar/MyExtensionPoint.java");

    PsiElement originalElement = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    PomTargetPsiElement pomTargetPsiElement = assertInstanceOf(myFixture.getElementAtCaret(), PomTargetPsiElement.class);
    DomTarget domTarget = assertInstanceOf(pomTargetPsiElement.getTarget(), DomTarget.class);
    PsiElement epPsiElement = domTarget.getNavigationElement();

    DocumentationProvider provider = DocumentationManager.getProviderFromElement(epPsiElement);

    String epDefinition = "[" + myModule.getName() + "] foo<br/>" +
                          "<b>bar</b> " +
                          "[extensionPointDocumentation.xml]<br/>" +
                          "<a href=\"psi_element://bar.MyExtensionPoint\"><code>MyExtensionPoint</code></a>";

    assertEquals(epDefinition,
                 provider.getQuickNavigateInfo(epPsiElement, originalElement));

    assertEquals("<em>EP Definition</em><br/>" +
                 epDefinition +
                 "<br/><br/>" +
                 "<em>EP Implementation</em>" +
                 "<html><head>    <style type=\"text/css\">        #error {            background-color: #eeeeee;            margin-bottom: 10px;        }        p {            margin: 5px 0;        }    </style></head>" +
                 "<body><small><b>bar</b></small><PRE>public interface <b>MyExtensionPoint</b></PRE>\n" +
                 "   MyExtensionPoint JavaDoc.</body></html>",
                 provider.generateDoc(epPsiElement, originalElement));
  }
}
