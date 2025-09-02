// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.documentation

import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.platform.backend.documentation.DocumentationData
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
      "<ul>" +
      "<li><a href=\"psi_element://#attribute:root__first-attribute\"><code>first-attribute</code></a></li>" +
      "<li><a href=\"psi_element://#attribute:root__secondAttribute\"><code>secondAttribute</code></a></li>" +
      "</ul>" +
      "<h5>Children</h5>" +
      "<ul>" +
      "<li><a href=\"psi_element://#element:root__first-level-child-1\"><code>&lt;first-level-child-1&gt;</code></a></li>" +
      "<li><a href=\"psi_element://#element:root__firstLevelChild2\"><code>&lt;firstLevelChild2&gt;</code></a></li>" +
      "<li><a href=\"psi_element://#element:root__deprecatedElement\"><code>&lt;deprecatedElement&gt;</code></a></li>" +
      "<li><a href=\"psi_element://#element:root__elementWithDeprecatedAttribute\"><code>&lt;elementWithDeprecatedAttribute&gt;</code></a></li>" +
      "<li><a href=\"psi_element://#element:root__elementWithCallouts\"><code>&lt;elementWithCallouts&gt;</code></a></li>" +
      "<li><a href=\"psi_element://#element:root__elementWithNotIncludedAttribute\"><code>&lt;elementWithNotIncludedAttribute&gt;</code></a></li>" +
      "<li><a href=\"psi_element://#element:root__elementWithChildrenDescription\"><code>&lt;elementWithChildrenDescription&gt;</code></a></li>" +
      "<li><a href=\"psi_element://#element:root__internalElement\"><code>&lt;internalElement&gt;</code></a> <i>internal</i></li>" +
      "<li><a href=\"psi_element://#element:root__elementWithInternalLinks\"><code>&lt;elementWithInternalLinks&gt;</code></a> <i>internal</i></li>" +
      "<li><a href=\"psi_element://#element:root__xi:include\"><code>&lt;xi:include&gt;</code></a></li>" +
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
      "The <code>first-level-child-1</code> description.<blockquote><p><icon src=\"AllIcons.General.Warning\"/> <b>Warning</b><br>\n" +
      " Some warning about <code>first-level-child-1</code>.</blockquote>" +
      "<h5>Requirement</h5>" +
      "<p>Required: no; additional details with an alias<br/>\n" +
      "<b>Additional detail about <code>first-level-child-1</code>.</b>" +
      "<h5>Default value</h5>" +
      "<p>Value of the <a href=\"psi_element://#element:idea-plugin__name\"><code>&lt;name&gt;</code></a> element. See <b>UI Path</b>." +
      "<h5>Children</h5>" +
      "<ul>" +
      "<li><a href=\"psi_element://#element:root__first-level-child-1__second-level-child\"><code>&lt;second-level-child&gt;</code></a> <i><b>required</b></i></li>" +
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
      "A <code>child-attribute</code> description. A link to <a href=\"psi_element://#element:root\"><code>&lt;root&gt;</code></a>." +
      "<blockquote><p><icon src=\"AllIcons.General.Warning\"/> <b>Warning</b><br>\n" +
      " Callout here.</blockquote>" +
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
      "<li><a href=\"psi_element://#attribute:root__firstLevelChild2__child-attribute-1\"><code>child-attribute-1</code></a> <i><b>required</b></i></li>" +
      "<li><a href=\"psi_element://#attribute:root__firstLevelChild2__child-attribute-2\"><code>child-attribute-2</code></a></li>" +
      "</ul>" +
      "<h5>Children</h5>" +
      "<ul>" +
      "<li><a href=\"psi_element://#element:root__firstLevelChild2__second-level-child-1\"><code>&lt;second-level-child-1&gt;</code></a></li>" +
      "<li><a href=\"psi_element://#element:root__firstLevelChild2__second-level-child\"><code>&lt;second-level-child&gt;</code></a> <i><b>required</b></i></li>" +
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
      "<li><a href=\"psi_element://#attribute:root__firstLevelChild2__child-attribute-1\"><code>child-attribute-1</code></a> <i><b>required</b></i></li>" +
      "<li><a href=\"psi_element://#attribute:root__firstLevelChild2__child-attribute-2\"><code>child-attribute-2</code></a></li>" +
      "</ul>" +
      "<h5>Children</h5>" +
      "<ul>" +
      "<li><a href=\"psi_element://#element:root__firstLevelChild2__second-level-child-1\"><code>&lt;second-level-child-1&gt;</code></a></li>" +
      "<li><a href=\"psi_element://#element:root__firstLevelChild2__second-level-child\"><code>&lt;second-level-child&gt;</code></a> <i><b>required</b></i></li>" +
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

  fun `test deprecated attribute`() {
    doTestDocContains(
      """
        <root>
          <elementWithDeprecatedAttribute deprecated-<caret>attribute=""/>
        </root>
      """.trimIndent(),
      "<p><a href=\"psi_element://#element:root\"><code>&lt;root&gt;</code></a> / <a href=\"psi_element://#element:root__elementWithDeprecatedAttribute\"><code>&lt;elementWithDeprecatedAttribute&gt;</code></a> / <b><code>@deprecated-attribute</code></b><hr/>\n" +
      "<b><i>Deprecated since 2005.1</i></b><br/><i>Use <a href=\"psi_element://#attribute:root__elementWithDeprecatedAttribute__new-attribute\"><code>new-attribute</code></a> instead.</i>" +
      "<p>The <code>deprecated-attribute</code> description."
    )
  }

  fun `test element with callouts`() {
    doTestDocContains(
      """
        <root>
          <elementWith<caret>Callouts/>
        </root>
      """.trimIndent(),
      "<p><a href=\"psi_element://#element:root\"><code>&lt;root&gt;</code></a> / <b><code>&lt;elementWithCallouts&gt;</code></b><hr/>\n" +
      "Dummy text 1." +
      "<blockquote>" +
      "<p><icon src=\"AllIcons.Actions.IntentionBulbGrey\"/> <b>Tip</b><br>\n" +
      " This is a 1st callout without style.\n" +
      "Dummy text 2.</blockquote>" +
      "<blockquote>" +
      "<p><icon src=\"AllIcons.General.Warning\"/> <b>Warning</b><br>\n" +
      " This is a 2nd callout - note.\n" +
      " Checking multiline." +
      "</blockquote>" +
      "<p>Dummy text 3." +
      "<blockquote>" +
      "<p><icon src=\"AllIcons.General.Warning\"/> <b>Warning</b><br>\n" +
      " This is a 3rd callout - warning." +
      "<p>New paragraph." +
      "</blockquote>" +
      "<p>Dummy text 4." +
      "<blockquote>" +
      "<p><icon src=\"AllIcons.General.Warning\"/> <b>Custom Title</b><br>\n" +
      " This is a 4th callout - warning with a custom title at the end." +
      "<p>New paragraph." +
      "</blockquote>" +
      "<p>Dummy text 5." +
      "<blockquote>" +
      "<p><icon src=\"AllIcons.General.Warning\"/> <b>Custom Title</b><br>\n" +
      " This is a 5th callout - warning with a custom title at the start." +
      "<p>New paragraph." +
      "</blockquote>" +
      "<p>Dummy text 6." +
      "<blockquote>" +
      "<p><icon src=\"AllIcons.Actions.IntentionBulbGrey\"/> <b>Custom Title</b><br>\n" +
      " This is a 6th callout - the implicit tip style and a custom title." +
      "<p>New paragraph." +
      "</blockquote>" +
      "<h5>Requirement</h5>" +
      "<p>Required: no"
    )
  }

  fun `test element with children description`() {
    doTestDocContains(
      """
        <root>
          <elementWith<caret>ChildrenDescription/>
        </root>
      """.trimIndent(),
      "<p><a href=\"psi_element://#element:root\"><code>&lt;root&gt;</code></a> / <b><code>&lt;elementWithChildrenDescription&gt;</code></b><hr/>\n" +
      "any" +
      "<h5>Children</h5>" +
      "<p>Test children description."
    )
  }

  fun `test attribute of a wildcard element`() {
    doTestDocContains(
      """
        <root>
          <anyNameElement attribute<caret>UnderWildcard="any"/>
        </root>
      """.trimIndent(),
      "<p><a href=\"psi_element://#element:root\"><code>&lt;root&gt;</code></a> / <code>*</code> / <b><code>@attributeUnderWildcard</code></b><hr/>\n" +
      "Description of <code>attributeUnderWildcard</code>."
    )
  }

  fun `test element under wildcard element`() {
    doTestDocContains(
      """
        <root>
          <anyNameElement>
            <childUnder<caret>Wildcard/>
          </anyNameElement>
        </root>
      """.trimIndent(),
      "<p><a href=\"psi_element://#element:root\"><code>&lt;root&gt;</code></a> / <code>*</code> / <b><code>&lt;childUnderWildcard&gt;</code></b><hr/>\n" +
      "Description of <code>childUnderWildcard</code>." +
      "<h5>Attributes</h5>" +
      "<ul>" +
      "<li><a href=\"psi_element://#attribute:root__*__childUnderWildcard__attributeOfElementUnderWildcard\"><code>attributeOfElementUnderWildcard</code></a></li>" +
      "</ul>"
    )
  }

  fun `test attribute of element under wildcard element`() {
    doTestDocContains(
      """
        <root>
          <anyNameElement>
            <childUnderWildcard attribute<caret>OfElementUnderWildcard="any"/>
          </anyNameElement>
        </root>
      """.trimIndent(),
      "<p><a href=\"psi_element://#element:root\"><code>&lt;root&gt;</code></a> / <code>*</code> / <a href=\"psi_element://#element:root__*__childUnderWildcard\"><code>&lt;childUnderWildcard&gt;</code></a> / <b><code>@attributeOfElementUnderWildcard</code></b><hr/>\n" +
      "Description of <code>attributeOfElementUnderWildcard</code>."
    )
  }

  fun `test an internal element`() {
    doTestDocContains(
      """
        <root>
          <internal<caret>Element/>
        </root>
      """.trimIndent(),
      "<p><a href=\"psi_element://#element:root\"><code>&lt;root&gt;</code></a> / <b><code>&lt;internalElement&gt;</code></b><hr/>\n" +
      "An internal element description." +
      "<h5>Attributes</h5>" +
      "<ul>" +
      "<li><a href=\"psi_element://#attribute:root__internalElement__internalAttribute\"><code>internalAttribute</code></a> <i>internal</i></li>" +
      "</ul>" +
      "<h5>Children</h5>" +
      "<ul>" +
      "<li><a href=\"psi_element://#element:root__internalElement__internalChildElement\"><code>&lt;internalChildElement&gt;</code></a> <i>internal</i></li>" +
      "</ul>" +
      "<h6><icon src=\"AllIcons.General.Warning\"/> <b>Internal Use Only</b></h6>" +
      "<p>An internal note for the element."
    )
  }

  fun `test an internal attribute`() {
    doTestDocContains(
      """
        <root>
          <internalElement internal<caret>Attribute="any"/>
        </root>
      """.trimIndent(),
      "<p><a href=\"psi_element://#element:root\"><code>&lt;root&gt;</code></a> / <a href=\"psi_element://#element:root__internalElement\"><code>&lt;internalElement&gt;</code></a> / <b><code>@internalAttribute</code></b><hr/>\n" +
      "Description of <code>internalAttribute</code>." +
      "<h6><icon src=\"AllIcons.General.Warning\"/> <b>Internal Use Only</b></h6>" +
      "<p>An internal note for the attribute."
    )
  }

  fun `test element with internal links in IntelliJ Platform project should be preserved`() {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, true)
    doTestDocContains(
      """
        <root>
          <element<caret>WithInternalLinks/>
        </root>
      """.trimIndent(),
      "<p><a href=\"psi_element://#element:root\"><code>&lt;root&gt;</code></a> / <b><code>&lt;elementWithInternalLinks&gt;</code></b><hr/>\n" +
      "<a href=\"https://example.com\">First</a> and <a href=\"https://example.com\"><code>second</code> internal</a>." +
      "<h5>Attributes</h5>" +
      "<ul>" +
      "<li><a href=\"psi_element://#attribute:root__elementWithInternalLinks__attributeWithInternalLinks\"><code>attributeWithInternalLinks</code></a> <i>internal</i></li>" +
      "</ul>" +
      "<h5>Children</h5>" +
      "<ul>" +
      "<li><a href=\"psi_element://#element:root__elementWithInternalLinks__internalChildElement\"><code>&lt;internalChildElement&gt;</code></a> <i>internal</i></li>" +
      "</ul>" +
      "<h6><icon src=\"AllIcons.General.Warning\"/> <b>Internal Use Only</b></h6>" +
      "<p>An <a href=\"https://example.com/2\">internal link note</a> for the <a href=\"psi_element://#element:root__any\"><code>element</code></a>."
    )
  }

  fun `test element with internal links in a not IntelliJ Platform project should be cleared`() {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, false)
    doTestDocContains(
      """
        <root>
          <element<caret>WithInternalLinks/>
        </root>
      """.trimIndent(),
      "<p><a href=\"psi_element://#element:root\"><code>&lt;root&gt;</code></a> / <b><code>&lt;elementWithInternalLinks&gt;</code></b><hr/>\n" +
      "<a href=\"https://example.com\">First</a> and <code>second</code> internal." +
      "<h5>Attributes</h5>" +
      "<ul>" +
      "<li><a href=\"psi_element://#attribute:root__elementWithInternalLinks__attributeWithInternalLinks\"><code>attributeWithInternalLinks</code></a> <i>internal</i></li>" +
      "</ul>" +
      "<h5>Children</h5>" +
      "<ul>" +
      "<li><a href=\"psi_element://#element:root__elementWithInternalLinks__internalChildElement\"><code>&lt;internalChildElement&gt;</code></a> <i>internal</i></li>" +
      "</ul>" +
      "<h6><icon src=\"AllIcons.General.Warning\"/> <b>Internal Use Only</b></h6>" +
      "<p>An internal link note for the <a href=\"psi_element://#element:root__any\"><code>element</code></a>."
    )
  }

  fun `test attribute with internal links in IntelliJ Platform project should be preserved`() {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, true)
    doTestDocContains(
      """
        <root>
          <elementWithInternalLinks attribute<caret>WithInternalLinks="any"/>
        </root>
      """.trimIndent(),
      "<p><a href=\"psi_element://#element:root\"><code>&lt;root&gt;</code></a> / <a href=\"psi_element://#element:root__elementWithInternalLinks\"><code>&lt;elementWithInternalLinks&gt;</code></a> / <b><code>@attributeWithInternalLinks</code></b><hr/>\n" +
      "<a href=\"https://example.com\">First</a> and <a href=\"https://example.com\"><code>second</code> internal</a>." +
      "<h6><icon src=\"AllIcons.General.Warning\"/> <b>Internal Use Only</b></h6>" +
      "<p>An <a href=\"https://example.com/2\">internal link note</a> for the <a href=\"psi_element://#attribute:root__any\"><code>attribute</code></a>."
    )
  }

  fun `test attribute with internal links in a not IntelliJ Platform project should be cleared`() {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, false)
    doTestDocContains(
      """
        <root>
          <elementWithInternalLinks attribute<caret>WithInternalLinks="any"/>
        </root>
      """.trimIndent(),
      "<p><a href=\"psi_element://#element:root\"><code>&lt;root&gt;</code></a> / <a href=\"psi_element://#element:root__elementWithInternalLinks\"><code>&lt;elementWithInternalLinks&gt;</code></a> / <b><code>@attributeWithInternalLinks</code></b><hr/>\n" +
      "<a href=\"https://example.com\">First</a> and <code>second</code> internal." +
      "<h6><icon src=\"AllIcons.General.Warning\"/> <b>Internal Use Only</b></h6>" +
      "<p>An internal link note for the <a href=\"psi_element://#attribute:root__any\"><code>attribute</code></a>."
    )
  }

  fun `test element with namespace`() {
    doTestDocContains(
      """
        <root xmlns:xi="http://www.w3.org/2001/XInclude">
          <xi:inc<caret>lude href="any"/>
        </root>
      """.trimIndent(),
      "<p><a href=\"psi_element://#element:root\"><code>&lt;root&gt;</code></a> / <b><code>&lt;xi:include&gt;</code></b><hr/>\n" +
      "Description of <code>xi:include</code>.<h5>Namespace</h5><p><code>xmlns:xi=&quot;http://www.w3.org/2001/XInclude&quot;</code>" +
      "<h5>Attributes</h5>" +
      "<ul>" +
      "<li><a href=\"psi_element://#attribute:root__xi:include__href\"><code>href</code></a></li>" +
      "</ul>" +
      "<h5>Children</h5>" +
      "<ul>" +
      "<li><a href=\"psi_element://#element:root__xi:include__xi:fallback\"><code>&lt;xi:fallback&gt;</code></a></li>" +
      "</ul>"
    )
  }

  fun `test attribute of element with namespace`() {
    doTestDocContains(
      """
        <root xmlns:xi="http://www.w3.org/2001/XInclude">
          <xi:include hr<caret>ef="any"/>
        </root>
      """.trimIndent(),
      "<p><a href=\"psi_element://#element:root\"><code>&lt;root&gt;</code></a> / <a href=\"psi_element://#element:root__xi:include\"><code>&lt;xi:include&gt;</code></a> / <b><code>@href</code></b><hr/>\n" +
      "Description of <code>xi:include@href</code>."
    )
  }

  fun `test element not included in the doc_provider context should not be documented`() {
    doTestDoesNotProvideDoc(
      """
        <root>
          <element<caret>NotIncludedInDocumentationProvider/>
        </root>
      """.trimIndent()
    )
  }

  fun `test child of an element not included in the doc_provider context should not be documented`() {
    doTestDoesNotProvideDoc(
      """
        <root>
          <elementNotIncludedInDocumentationProvider>
            <childNotIncluded<caret>InDocumentationProvider/>
          </elementNotIncludedInDocumentationProvider>
        </root>
      """.trimIndent()
    )
  }

  fun `test child included in doc_provider, but under an element not enabled in the doc_provider context should not be documented`() {
    doTestDoesNotProvideDoc(
      """
        <root>
          <elementNotIncludedInDocumentationProvider>
            <childIncluded<caret>InDocumentationProvider/>
          </elementNotIncludedInDocumentationProvider>
        </root>
      """.trimIndent()
    )
  }

  fun `test not included attribute should not be documented`() {
    doTestDoesNotProvideDoc(
      """
        <root>
          <elementWithNotIncludedAttribute notIncluded<caret>Attribute="any"/>
        </root>
      """.trimIndent()
    )
  }

  fun `test included attribute, but under an element not enabled in the doc_provider context should not be documented`() {
    doTestDoesNotProvideDoc(
      """
        <root>
          <elementNotIncludedInDocumentationProvider attribute<caret>IncludedInDocumentationProvider="any"/>
        </root>
      """.trimIndent()
    )
  }

  private fun doTestDocContains(@Language("XML") fileText: String, @Language("HTML") expectedDoc: String) {
    val data = findDocumentationData(fileText)
    assertNotNull(data)
    assertEquals(expectedDoc, data!!.html)
  }

  private fun doTestDoesNotProvideDoc(@Language("XML") fileText: String) {
    val data = findDocumentationData(fileText)
    assertNull(data)
  }

  private fun findDocumentationData(fileText: String): DocumentationData? {
    myFixture.configureByText(TEST_XML_FILE_NAME, fileText)
    val targets = IdeDocumentationTargetProvider.getInstance(project)
      .documentationTargets(myFixture.editor, myFixture.file, myFixture.caretOffset)
    assertSize(1, targets)
    val target = targets.single()
    val data = computeDocumentationBlocking(target.createPointer())
    return data
  }

}

private class TestXmlDescriptorDocumentationTargetProvider : AbstractXmlDescriptorDocumentationTargetProvider() {

  override val docYamlCoordinates = DocumentationDataCoordinates("FAKE_URL", "/documentation/xml-plugin-descriptor-documentation-test.yaml")

  override fun isApplicable(element: PsiElement, originalElement: PsiElement?): Boolean {
    return originalElement?.containingFile?.name == TEST_XML_FILE_NAME
  }
}
