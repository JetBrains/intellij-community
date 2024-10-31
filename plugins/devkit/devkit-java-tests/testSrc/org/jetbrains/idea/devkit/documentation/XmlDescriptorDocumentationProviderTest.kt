// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.documentation

import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.platform.backend.documentation.impl.computeDocumentationBlocking
import com.intellij.psi.PsiElement
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import org.intellij.lang.annotations.Language

private const val TEST_XML_FILE_NAME = "xml-plugin-descriptor-documentation-provider-test.xml"

class XmlDescriptorDocumentationProviderTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {

  override fun setUp() {
    super.setUp()
    ApplicationManager.getApplication().extensionArea.getExtensionPoint(PsiDocumentationTargetProvider.EP_NAME)
      .registerExtension(TestXmlDescriptorDocumentationTargetProvider(), project)
  }

  fun `test root element doc`() {
    doTestDocContains(
      "<ro<caret>ot></root>",
      "<p><b><code>&lt;root&gt;</code></b><hr/>\n" +
      "The <i>test.xml</i> file root element. A link to an <a href=\"psi_element://#attribute:root__firstLevelChild2__child-attribute-2\"><code>attribute</code></a>." +
      "<h5>Requirement</h5>" +
      "<p>Required: <b>yes</b>" +
      "<h5>Attributes</h5>" +
      "<ul><li><a href=\"psi_element://#attribute:root__first-attribute\"><code>first-attribute</code></a></li><li><a href=\"psi_element://#attribute:root__secondAttribute\"><code>secondAttribute</code></a></li></ul>" +
      "<h5>Children</h5>" +
      "<ul>" +
      "<li><a href=\"psi_element://#element:root__first-level-child-1\"><code>&lt;first-level-child-1&gt;</code></a></li>" +
      "<li><a href=\"psi_element://#element:root__firstLevelChild2\"><code>&lt;firstLevelChild2&gt;</code></a></li>" +
      "<li><a href=\"psi_element://#element:root__deprecatedElement\"><code>&lt;deprecatedElement&gt;</code></a></li>"+
      "</ul>"
    )
  }

  fun `test child element doc`() {
    doTestDocContains(
      """
        <root>
          <first-<caret>level-child-1/>
          <firstLevelChild2 child-attribute-1="any">
            <second-level-child-1/>
          </firstLevelChild2>
        </root>
      """.trimIndent(),
      "<p><a href=\"psi_element://#element:root\"><code>&lt;root&gt;</code></a> / <b><code>&lt;first-level-child-1&gt;</code></b><hr/>\n" +
      "The <code>first-level-child-1</code> description.<blockquote><p>Some warning about <code>first-level-child-1</code>.</blockquote>" +
      "<h5>Requirement</h5>" +
      "<p>Required: no; additional details with an alias<br/>\n" +
      "<b>Additional detail about <code>first-level-child-1</code>.</b>" +
      "<h5>Default value</h5>" +
      "<p>Value of the <a href=\"psi_element://#element:idea-plugin__name\"><code>&lt;name&gt;</code></a> element. See <b>UI Path</b>." +
      "<h5>Children</h5>" +
      "<ul>" +
      "<li><a href=\"psi_element://#element:root__first-level-child-1__second-level-child\"><code>&lt;second-level-child&gt;</code></a> <i>required</i></li>" +
      "</ul>" +
      "<h5>Examples</h5>" +
      "<ul><li>An example description 2:</li></ul>" +
      "<pre><code>" +
      "<span style=\"\">&lt;</span><span style=\"color:#000080;font-weight:bold;\">first-level-child</span><span style=\"\">&gt;</span><span style=\"\">any1</span><span style=\"\">&lt;/</span><span style=\"color:#000080;font-weight:bold;\">first-level-child</span><span style=\"\">&gt;</span>" +
      "</code></pre>" +
      "<ul><li>An example description 1:</li></ul>" +
      "<pre><code>" +
      "<span style=\"\">&lt;</span><span style=\"color:#000080;font-weight:bold;\">first-level-child</span><span style=\"\">&gt;</span><span style=\"\">any2</span><span style=\"\">&lt;/</span><span style=\"color:#000080;font-weight:bold;\">first-level-child</span><span style=\"\">&gt;</span>" +
      "</code></pre>"
    )
  }

  fun `test child element attribute doc`() {
    doTestDocContains(
      """
        <root>
          <first-level-child-1/>
          <firstLevelChild2 child<caret>-attribute-1="any">
            <second-level-child-1/>
          </firstLevelChild2>
        </root>
      """.trimIndent(),
      "<p><a href=\"psi_element://#element:root\"><code>&lt;root&gt;</code></a> / <a href=\"psi_element://#element:root__firstLevelChild2\"><code>&lt;firstLevelChild2&gt;</code></a> / <b><code>@child-attribute-1</code></b><hr/>\n" +
      "A <code>child-attribute</code> description. A link to <a href=\"psi_element://#element:root\"><code>&lt;root&gt;</code></a>.<blockquote><p>Callout here.</blockquote>" +
      "<p>Required: <b>yes</b>"
    )
  }

  fun `test element with explicit, referenced and itself children`() {
    doTestDocContains(
      """
        <root>
          <first-level-child-1/>
          <first<caret>LevelChild2>
            <second-level-child-1/>
          </firstLevelChild2>
        </root>
      """.trimIndent(),
      "<p><a href=\"psi_element://#element:root\"><code>&lt;root&gt;</code></a> / <b><code>&lt;firstLevelChild2&gt;</code></b><hr/>\n" +
      "<i>Available: since 2021.2</i><p>The <code>secondLevelChild2</code> description.\n" +
      "A link to <a href=\"psi_element://#attribute:root__first-attribute\"><code>first-attribute</code></a>." +
      "<h5>Requirement</h5>" +
      "<p>Required: no" +
      "<h5>Default value</h5>" +
      "<p>Value of the <a href=\"psi_element://#element:root__first-level-child\"><code>&lt;first-level-child1&gt;</code></a> element." +
      "<h5>Attributes</h5>" +
      "<ul>" +
      "<li><a href=\"psi_element://#attribute:root__firstLevelChild2__child-attribute-1\"><code>child-attribute-1</code></a> <i>required</i></li>" +
      "<li><a href=\"psi_element://#attribute:root__firstLevelChild2__child-attribute-2\"><code>child-attribute-2</code></a></li>" +
      "</ul>" +
      "<h5>Children</h5>" +
      "<ul>" +
      "<li><a href=\"psi_element://#element:root__firstLevelChild2__second-level-child-1\"><code>&lt;second-level-child-1&gt;</code></a></li>" +
      "<li><a href=\"psi_element://#element:root__first-level-child-1__second-level-child\"><code>&lt;second-level-child&gt;</code></a> <i>required</i></li>" +
      "<li><code>&lt;firstLevelChild2&gt;</code></li>" +
      "</ul>" +
      "<h5>Example</h5>" +
      "<pre><code>" +
      "<span style=\"\">&lt;</span><span style=\"color:#000080;font-weight:bold;\">first-level-child</span><span style=\"\">&gt;</span><span style=\"\">any</span><span style=\"\">&lt;/</span><span style=\"color:#000080;font-weight:bold;\">first-level-child</span><span style=\"\">&gt;</span>" +
      "</code></pre>"
    )
  }

  fun `test deep self-containing element doc`() {
    doTestDocContains(
      """
        <root>
          <first-level-child-1/>
          <firstLevelChild2>
            <second-level-child-1/>
            <firstLevelChild2>
              <firstLevelChild2>
                <firstLevelChild2>
                  <firstLevelChild2>
                    <firstLevelChild2>
                      <first<caret>LevelChild2>
                          
                      </firstLevelChild2>
                    </firstLevelChild2>
                  </firstLevelChild2>
                </firstLevelChild2>
              </firstLevelChild2>
            </firstLevelChild2>
          </firstLevelChild2>
        </root>
      """.trimIndent(),
      "<p><a href=\"psi_element://#element:root\"><code>&lt;root&gt;</code></a> / <b><code>&lt;firstLevelChild2&gt;</code></b><hr/>\n" +
      "<i>Available: since 2021.2</i><p>The <code>secondLevelChild2</code> description.\n" +
      "A link to <a href=\"psi_element://#attribute:root__first-attribute\"><code>first-attribute</code></a>." +
      "<h5>Requirement</h5>" +
      "<p>Required: no" +
      "<h5>Default value</h5>" +
      "<p>Value of the <a href=\"psi_element://#element:root__first-level-child\"><code>&lt;first-level-child1&gt;</code></a> element." +
      "<h5>Attributes</h5>" +
      "<ul>" +
      "<li><a href=\"psi_element://#attribute:root__firstLevelChild2__child-attribute-1\"><code>child-attribute-1</code></a> <i>required</i></li>" +
      "<li><a href=\"psi_element://#attribute:root__firstLevelChild2__child-attribute-2\"><code>child-attribute-2</code></a></li>" +
      "</ul>" +
      "<h5>Children</h5>" +
      "<ul>" +
      "<li><a href=\"psi_element://#element:root__firstLevelChild2__second-level-child-1\"><code>&lt;second-level-child-1&gt;</code></a></li>" +
      "<li><a href=\"psi_element://#element:root__first-level-child-1__second-level-child\"><code>&lt;second-level-child&gt;</code></a> <i>required</i></li>" +
      "<li><code>&lt;firstLevelChild2&gt;</code></li>" +
      "</ul>" +
      "<h5>Example</h5>" +
      "<pre><code>" +
      "<span style=\"\">&lt;</span><span style=\"color:#000080;font-weight:bold;\">first-level-child</span><span style=\"\">&gt;</span><span style=\"\">any</span><span style=\"\">&lt;/</span><span style=\"color:#000080;font-weight:bold;\">first-level-child</span><span style=\"\">&gt;</span>" +
      "</code></pre>"
    )
  }

  fun `test deprecated element`() {
    doTestDocContains(
      """
        <root>
          <first-level-child-1/>
          <firstLevelChild2/>
          <deprecated<caret>Element/>
        </root>
      """.trimIndent(),
      "<p><a href=\"psi_element://#element:root\"><code>&lt;root&gt;</code></a> / <b><code>&lt;deprecatedElement&gt;</code></b><hr/>\n" +
      "<b><i>Deprecated since 2020.1</i></b><br/>" +
      "<i>Do not use it in new plugins.</i>\n" +
      "<i>See <a href=\"https://example.com\">Components</a> for the migration guide.</i>" +
      "<p>The <code>deprecatedElement</code> description." +
      "<h5>Requirement</h5>" +
      "<p>Required: no"
    )
  }

  private fun doTestDocContains(@Language("XML") fileText: String, @Language("HTML") expectedDoc: String) {
    myFixture.configureByText(TEST_XML_FILE_NAME, fileText)
    val targets = IdeDocumentationTargetProvider.getInstance(project)
      .documentationTargets(myFixture.editor, myFixture.file, myFixture.caretOffset)
    assertSize(1, targets)
    val target = targets.single()
    val data = computeDocumentationBlocking(target.createPointer())
    assertNotNull(data)
    assertEquals(expectedDoc, data!!.html)
  }
}

private class TestXmlDescriptorDocumentationTargetProvider : AbstractXmlDescriptorDocumentationTargetProvider() {

  override val docYamlCoordinates = DocumentationDataCoordinates("any", "/documentation/xml-plugin-descriptor-documentation-test.yaml")

  override fun isApplicable(element: PsiElement, originalElement: PsiElement?): Boolean {
    return originalElement?.containingFile?.name == TEST_XML_FILE_NAME
  }
}
