package org.jetbrains.idea.devkit.references.extensions;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

public class ExtensionPointDocumentationProviderTest extends LightCodeInsightFixtureTestCase {

  @Override
  public String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "references/extensions";
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

    assertEquals("<div class='definition'><pre><b>bar</b> [foo]<br>" +
                 "<a href=\"psi_element://bar.MyExtensionPoint\"><code>MyExtensionPoint</code></a><br>" +
                 "extensionPointDocumentation.xml" +
                 "<table class='sections'>" +
                 "<tr><td valign='top' class='section'><p>attributeName:</td><td valign='top'><a href=\"psi_element://java.lang.String\"><code>String</code></a></td>" +
                 "<tr><td valign='top' class='section'><p>&lt;tagName&gt;:</td><td valign='top'><a href=\"psi_element://java.lang.Integer\"><code>Integer</code></a></td></table>" +
                 "</pre></div>" +
                 "<div class='content'>" +
                 "<em>Extension Point Implementation Class</em>" +
                 "<div class='definition'><pre>bar<br>public interface <b>MyExtensionPoint</b></pre></div>" +
                 "<div class='content'>\n   MyExtensionPoint JavaDoc.\n </div><table class='sections'><p></table></div>",
                 provider.generateDoc(docElement, null));
  }

  public void testExtensionPointDocumentationQualifiedName() {
    myFixture.configureByFiles("extensionPointDocumentationQualifiedName.xml", "bar/MyExtensionPoint.java");

    final PsiElement docElement =
      DocumentationManager.getInstance(getProject()).findTargetElement(myFixture.getEditor(),
                                                                       myFixture.getFile());
    DocumentationProvider provider = DocumentationManager.getProviderFromElement(docElement);

    String epDefinition = "[" + myModule.getName() + "]<br/>" +
                          "<b>com.my.bar</b> (extensionPointDocumentationQualifiedName.xml)<br/>" +
                          "<a href=\"psi_element://bar.MyExtensionPoint\"><code>MyExtensionPoint</code></a>";

    assertEquals(epDefinition,
                 provider.getQuickNavigateInfo(docElement, null));

    assertEquals("<div class='definition'><pre><b>com.my.bar</b><br>" +
                 "<a href=\"psi_element://bar.MyExtensionPoint\"><code>MyExtensionPoint</code></a><br>" +
                 "extensionPointDocumentationQualifiedName.xml" +
                 "<table class='sections'>" +
                 "<tr><td valign='top' class='section'><p>attributeName:</td><td valign='top'><a href=\"psi_element://java.lang.String\"><code>String</code></a></td>" +
                 "<tr><td valign='top' class='section'><p>&lt;tagName&gt;:</td><td valign='top'><a href=\"psi_element://java.lang.Integer\"><code>Integer</code></a></td></table>" +
                 "</pre></div>" +
                 "<div class='content'>" +
                 "<em>Extension Point Implementation Class</em>" +
                 "<div class='definition'><pre>bar<br>public interface <b>MyExtensionPoint</b></pre></div>" +
                 "<div class='content'>\n   MyExtensionPoint JavaDoc.\n </div><table class='sections'><p></table></div>",
                 provider.generateDoc(docElement, null));
  }
}
