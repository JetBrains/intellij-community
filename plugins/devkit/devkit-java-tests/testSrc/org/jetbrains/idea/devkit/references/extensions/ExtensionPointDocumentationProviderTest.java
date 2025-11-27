// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    doBeanClassExtensionPointTest("beanClassExtensionPointDocumentation.xml",
                                  "beanClassExtensionPointDocumentation.xml");
  }

  public void testBeanClassExtensionPointQualifiedNameDocumentation() {
    doBeanClassExtensionPointTest("beanClassExtensionPointQualifiedNameDocumentation.xml",
                                  "beanClassExtensionPointQualifiedNameDocumentation.xml");
  }

  public void testBeanClassExtensionPointDocumentationWhenFileNameIsPluginXml() {
    doBeanClassExtensionPointTest("plugin.xml",
                                  "plugin.xml (foo.bar)");
  }

  private void doBeanClassExtensionPointTest(String pluginXmlFileName, String expectedEpLocationString) {
    myFixture.configureByFiles(pluginXmlFileName,
                               "bar/MyExtensionPoint.java", "bar/MyExtension.java");
    myFixture.addClass("package com.intellij.openapi.extensions; public @interface RequiredElement {}");
    myFixture.addClass("package com.intellij.util.xmlb.annotations; public @interface Attribute {}");
    myFixture.addClass("package com.intellij.util.xmlb.annotations; public @interface Tag {}");

    final PsiElement docElement =
      DocumentationManager.getInstance(getProject()).findTargetElement(myFixture.getEditor(),
                                                                       myFixture.getFile());
    DocumentationProvider provider = DocumentationManager.getProviderFromElement(docElement);

    //language=HTML
    String epDefinition = "[" + getModule().getName() + "]" +
                          "<br/><b>foo.bar</b> (" + pluginXmlFileName + ")<br/>" +
                          "<a href=\"psi_element://bar.MyExtensionPoint\"><code>MyExtensionPoint</code></a><br/>" +
                          "<a href=\"psi_element://bar.MyExtension\"><code>MyExtension</code></a>";
    assertEquals(epDefinition,
                 provider.getQuickNavigateInfo(docElement, getOriginalElement()));

    assertEquals(
      //language=HTML
      "<div class=\"definition\"><pre><b>foo.bar</b></pre><icon src=\"AllIcons.Nodes.Plugin\"/>&nbsp;" + expectedEpLocationString + "</div>" +
      "<div class=\"content\"><h4>Extension Point Bean</h4><p>Extension instances provide data via <a href=\"psi_element://bar.MyExtensionPoint\"><code>MyExtensionPoint</code></a>.</p><p><b>Field Bindings</b></p><table class=\"sections\"><tr><td class=\"section\" valign=\"top\"><p><a href=\"psi_element://bar.MyExtensionPoint#implementationClass\"><code>implementationClass</code></a></p></td><td valign=\"top\">String (required)</td></tr><tr><td class=\"section\" valign=\"top\"><p><a href=\"psi_element://bar.MyExtensionPoint#stringCanBeEmpty\"><code>stringCanBeEmpty</code></a></p></td><td valign=\"top\">String (required, empty allowed)</td></tr><tr><td class=\"section\" valign=\"top\"><p><a href=\"psi_element://bar.MyExtensionPoint#intValue\"><code>&lt;intValue&gt;</code></a></p></td><td valign=\"top\">Integer</td></tr></table><h4>Extension Point Implementation</h4><p>Extension instances implement <a href=\"psi_element://bar.MyExtension\"><code>MyExtension</code></a>.</p><hr/>Additional resources:<ul><li><a href=\"https://jb.gg/ipe?extensions=foo.bar\">EP Implementations in Open Source Plugins</a></li><li><a href=\"https://plugins.jetbrains.com/docs/intellij/plugin-extension-points.html\">Documentation: Extension Points</a></li><li><a href=\"https://plugins.jetbrains.com/docs/intellij/plugin-extensions.html\">Documentation: Extensions</a></li></ul></div>",
      provider.generateDoc(docElement, getOriginalElement()));
  }

  public void testInterfaceExtensionPointDocumentation() {
    myFixture.configureByFiles("interfaceExtensionPointDocumentation.xml",
                               "bar/MyExtension.java");

    final PsiElement docElement =
      DocumentationManager.getInstance(getProject()).findTargetElement(myFixture.getEditor(),
                                                                       myFixture.getFile());
    DocumentationProvider provider = DocumentationManager.getProviderFromElement(docElement);

    //language=HTML
    String epDefinition = "[" + getModule().getName() + "]" +
                          "<br/><b>foo.bar</b> (interfaceExtensionPointDocumentation.xml)<br/>" +
                          "<a href=\"psi_element://bar.MyExtension\"><code>MyExtension</code></a>";
    assertEquals(epDefinition,
                 provider.getQuickNavigateInfo(docElement, getOriginalElement()));

    assertEquals(
      //language=HTML
      "<div class=\"definition\"><pre><b>foo.bar</b></pre><icon src=\"AllIcons.Nodes.Plugin\"/>&nbsp;interfaceExtensionPointDocumentation.xml</div><div class=\"content\"><h4>Extension Point Implementation</h4><p>Extension instances implement <a href=\"psi_element://bar.MyExtension\"><code>MyExtension</code></a>.</p><hr/>Additional resources:<ul><li><a href=\"https://jb.gg/ipe?extensions=foo.bar\">EP Implementations in Open Source Plugins</a></li><li><a href=\"https://plugins.jetbrains.com/docs/intellij/plugin-extension-points.html\">Documentation: Extension Points</a></li><li><a href=\"https://plugins.jetbrains.com/docs/intellij/plugin-extensions.html\">Documentation: Extensions</a></li></ul></div>",
      provider.generateDoc(docElement, getOriginalElement())
    );
  }
}
