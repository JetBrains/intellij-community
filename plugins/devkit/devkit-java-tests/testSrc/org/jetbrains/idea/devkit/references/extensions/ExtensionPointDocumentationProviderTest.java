package org.jetbrains.idea.devkit.references.extensions;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/references/extensions")
public class ExtensionPointDocumentationProviderTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  public String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "references/extensions";
  }

  private PsiElement getOriginalElement() {
    return myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
  }

  public void testBeanClassExtensionPointDocumentation() {
    doBeanClassExtensionPointTest("beanClassExtensionPointDocumentation.xml");
  }

  public void testBeanClassExtensionPointQualifiedNameDocumentation() {
    doBeanClassExtensionPointTest("beanClassExtensionPointQualifiedNameDocumentation.xml");
  }

  private void doBeanClassExtensionPointTest(String pluginXml) {
    myFixture.configureByFiles(pluginXml,
                               "bar/MyExtensionPoint.java", "bar/MyExtension.java");
    myFixture.addClass("package com.intellij.openapi.extensions; public @interface RequiredElement {}");
    myFixture.addClass("package com.intellij.util.xmlb.annotations; public @interface Attribute {}");
    myFixture.addClass("package com.intellij.util.xmlb.annotations; public @interface Tag {}");

    final PsiElement docElement =
      DocumentationManager.getInstance(getProject()).findTargetElement(myFixture.getEditor(),
                                                                       myFixture.getFile());
    DocumentationProvider provider = DocumentationManager.getProviderFromElement(docElement);

    String epDefinition = "[" + getModule().getName() + "]" +
                          "<br/><b>foo.bar</b> (" + pluginXml + ")<br/>" +
                          "<a href=\"psi_element://bar.MyExtensionPoint\"><code>MyExtensionPoint</code></a><br/>" +
                          "<a href=\"psi_element://bar.MyExtension\"><code>MyExtension</code></a>";
    assertEquals(epDefinition,
                 provider.getQuickNavigateInfo(docElement, getOriginalElement()));

    assertEquals(
      "<div class=\"definition\"><pre><b>foo.bar</b><br/>" +
      pluginXml +
      "<div class='definition'><pre><span style=\"color:#000080;font-weight:bold;\">public</span> <span style=\"color:#000080;font-weight:bold;\">class</span> <span style=\"color:#000000;\">MyExtensionPoint</span></pre></div><div class='content'>\n" +
      "  MyExtensionPoint JavaDoc.\n" +
      " </div><table class='sections'><p></table><div class=\"bottom\"><icon src=\"AllIcons.Nodes.Package\">&nbsp;<a href=\"psi_element://bar\"><code><span style=\"color:#000000;\">bar</span></code></a></div><table class=\"sections\"><tr><td class=\"section\" valign=\"top\"><p><a href=\"psi_element://bar.MyExtensionPoint#implementationClass\"><code>implementationClass</code></a></p></td><td valign=\"top\">String (required)</td></tr><tr><td class=\"section\" valign=\"top\"><p><a href=\"psi_element://bar.MyExtensionPoint#stringCanBeEmpty\"><code>stringCanBeEmpty</code></a></p></td><td valign=\"top\">String (required, empty allowed)</td></tr><tr><td class=\"section\" valign=\"top\"><p><a href=\"psi_element://bar.MyExtensionPoint#intValue\"><code>&lt;intValue&gt;</code></a></p></td><td valign=\"top\">Integer</td></tr><br/></table></pre></div><div class=\"content\"><a href=\"https://jb.gg/ipe?extensions=foo.bar\">Show Usages in IntelliJ Platform Explorer</a></div><div class=\"content\"><h2>Extension Point Implementation</h2><div class='definition'><pre><span style=\"color:#000080;font-weight:bold;\">public</span> <span style=\"color:#000080;font-weight:bold;\">interface</span> <span style=\"color:#000000;\">MyExtension</span></pre></div><div class='content'>\n" +
      "  My Extension Javadoc.\n" +
      " </div><table class='sections'><p></table><div class=\"bottom\"><icon src=\"AllIcons.Nodes.Package\">&nbsp;<a href=\"psi_element://bar\"><code><span style=\"color:#000000;\">bar</span></code></a></div></div>",
      provider.generateDoc(docElement, getOriginalElement()));
  }

  public void testInterfaceExtensionPointDocumentation() {
    myFixture.configureByFiles("interfaceExtensionPointDocumentation.xml",
                               "bar/MyExtension.java");

    final PsiElement docElement =
      DocumentationManager.getInstance(getProject()).findTargetElement(myFixture.getEditor(),
                                                                       myFixture.getFile());
    DocumentationProvider provider = DocumentationManager.getProviderFromElement(docElement);

    String epDefinition = "[" + getModule().getName() + "]" +
                          "<br/><b>foo.bar</b> (interfaceExtensionPointDocumentation.xml)<br/>" +
                          "<a href=\"psi_element://bar.MyExtension\"><code>MyExtension</code></a>";
    assertEquals(epDefinition,
                 provider.getQuickNavigateInfo(docElement, getOriginalElement()));

    assertEquals(
      "<div class=\"definition\"><pre><b>foo.bar</b><br/>interfaceExtensionPointDocumentation.xml</pre></div><div class=\"content\"><a href=\"https://jb.gg/ipe?extensions=foo.bar\">Show Usages in IntelliJ Platform Explorer</a></div><div class=\"content\"><h2>Extension Point Implementation</h2><div class='definition'><pre><span style=\"color:#000080;font-weight:bold;\">public</span> <span style=\"color:#000080;font-weight:bold;\">interface</span> <span style=\"color:#000000;\">MyExtension</span></pre></div><div class='content'>\n" +
      "  My Extension Javadoc.\n" +
      " </div><table class='sections'><p></table><div class=\"bottom\"><icon src=\"AllIcons.Nodes.Package\">&nbsp;<a href=\"psi_element://bar\"><code><span style=\"color:#000000;\">bar</span></code></a></div></div>",
      provider.generateDoc(docElement, getOriginalElement()));
  }
}