// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.module.PluginModuleType

class ContentModuleNamespaceInspectionTest : JavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(ContentModuleNamespaceInspection())
  }

  fun `test report missing namespace for public module`() {
    addModule("com.example.publicmodule")
    myFixture.addXmlFile("com.example.publicmodule/com.example.publicmodule.xml", """
      <idea-plugin visibility="public">
      </idea-plugin>
    """.trimIndent())
    addModule("com.example.privatemodule")
    myFixture.addXmlFile("com.example.privatemodule/com.example.privatemodule.xml", """
      <idea-plugin> <!--private by default-->
      </idea-plugin>
    """.trimIndent())

    val testedFile = myFixture.addXmlFile("plugin.xml", """
      <idea-plugin>
        <<error descr="Namespace is required for 'content' with non-private modules">content</error>>
          <module name="com.example.privatemodule"/>
          <module name="com.example.publicmodule"/>
        </content>
      </idea-plugin>
    """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test report missing namespace for internal module`() {
    addModule("com.example.internalmodule")
    myFixture.addXmlFile("com.example.internalmodule/com.example.internalmodule.xml", """
      <idea-plugin visibility='internal'>
      </idea-plugin>
    """.trimIndent())
    addModule("com.example.privatemodule")
    myFixture.addXmlFile("com.example.privatemodule/com.example.privatemodule.xml", """
      <idea-plugin visibility='private'>
      </idea-plugin>
    """.trimIndent())

    val testedFile = myFixture.addXmlFile("plugin.xml", """
      <idea-plugin>
        <<error descr="Namespace is required for 'content' with non-private modules">content</error>>
          <module name="com.example.internalmodule"/>
          <module name="com.example.privatemodule"/>
        </content>
      </idea-plugin>
    """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test should not report missing namespace for explicitly private module`() {
    addModule("com.example.privatemodule")
    myFixture.addXmlFile("com.example.privatemodule/com.example.privatemodule.xml", """
      <idea-plugin>
      </idea-plugin>
    """.trimIndent())

    val testedFile = myFixture.addXmlFile("plugin.xml", """
      <idea-plugin visibility="private">
        <content>
          <module name="com.example.privatemodule"/>
        </content>
      </idea-plugin>
    """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test should not report missing namespace for implicitly private module`() {
    addModule("com.example.privatemodule")
    myFixture.addXmlFile("com.example.privatemodule/com.example.privatemodule.xml", """
      <idea-plugin>
      </idea-plugin>
    """.trimIndent())

    val testedFile = myFixture.addXmlFile("plugin.xml", """
      <idea-plugin>
        <content>
          <module name="com.example.privatemodule"/>
        </content>
      </idea-plugin>
    """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test report invalid namespace too short`() {
    val testedFile = myFixture.addXmlFile("plugin.xml", """
      <idea-plugin>
        <content namespace="<error descr="Namespace length must be between 5 and 30 characters">abc</error>">
        </content>
      </idea-plugin>
    """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test report invalid namespace too long`() {
    val testedFile = myFixture.addXmlFile("plugin.xml", """
      <idea-plugin>
        <content namespace="<error descr="Namespace length must be between 5 and 30 characters">this_namespace_is_way_too_long_for_validation</error>">
        </content>
      </idea-plugin>
    """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test report invalid namespace with invalid characters`() {
    val testedFile = myFixture.addXmlFile("plugin.xml", """
      <idea-plugin>
        <content namespace="<error descr="Namespace must match [a-zA-Z0-9]+([_-][a-zA-Z0-9]+)*">invalid@namespace</error>">
        </content>
      </idea-plugin>
    """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test report invalid namespace starting with underscore`() {
    val testedFile = myFixture.addXmlFile("plugin.xml", """
      <idea-plugin>
        <content namespace="<error descr="Namespace must match [a-zA-Z0-9]+([_-][a-zA-Z0-9]+)*">_invalid</error>">
        </content>
      </idea-plugin>
    """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test report invalid namespace with consecutive underscores`() {
    val testedFile = myFixture.addXmlFile("plugin.xml", """
      <idea-plugin>
        <content namespace="<error descr="Namespace must match [a-zA-Z0-9]+([_-][a-zA-Z0-9]+)*">invalid__namespace</error>">
        </content>
      </idea-plugin>
    """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test should not report valid namespace simple`() {
    val testedFile = myFixture.addXmlFile("plugin.xml", """
      <idea-plugin>
        <content namespace="mycompany">
        </content>
      </idea-plugin>
    """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test should not report valid namespace with underscores`() {
    val testedFile = myFixture.addXmlFile("plugin.xml", """
      <idea-plugin>
        <content namespace="my_company_namespace">
        </content>
      </idea-plugin>
    """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test should not report valid namespace with hyphens`() {
    val testedFile = myFixture.addXmlFile("plugin.xml", """
      <idea-plugin>
        <content namespace="my-company-namespace">
        </content>
      </idea-plugin>
    """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test should not report valid namespace with numbers`() {
    val testedFile = myFixture.addXmlFile("plugin.xml", """
      <idea-plugin>
        <content namespace="company123">
        </content>
      </idea-plugin>
    """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test report mismatched namespaces between content elements`() {
    addModule("com.example.module1")
    addModule("com.example.module2")
    myFixture.addXmlFile("com.example.module1/com.example.module1.xml", """
      <idea-plugin visibility="public">
      </idea-plugin>
    """.trimIndent())
    myFixture.addXmlFile("com.example.module2/com.example.module2.xml", """
      <idea-plugin visibility="public">
      </idea-plugin>
    """.trimIndent())

    val testedFile = myFixture.addXmlFile("plugin.xml", """
      <idea-plugin>
        <content namespace="first-namespace">
          <module name="com.example.module1"/>
        </content>
        <content namespace="<error descr="All content elements must define the same namespace (first namespace: 'first-namespace')">second-namespace</error>">
          <module name="com.example.module2"/>
        </content>
      </idea-plugin>
    """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test report mismatched namespace when first is empty`() {
    addModule("com.example.module1")
    addModule("com.example.module2")
    myFixture.addXmlFile("com.example.module1/com.example.module1.xml", """
      <idea-plugin visibility="private">
      </idea-plugin>
    """.trimIndent())
    myFixture.addXmlFile("com.example.module2/com.example.module2.xml", """
      <idea-plugin visibility="public">
      </idea-plugin>
    """.trimIndent())

    val testedFile = myFixture.addXmlFile("plugin.xml", """
      <idea-plugin>
        <content>
          <module name="com.example.module1"/>
        </content>
        <content namespace="<error descr="All content elements must define the same namespace (first namespace: 'null')">second-namespace</error>">
          <module name="com.example.module2"/>
        </content>
      </idea-plugin>
    """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test should not report matching namespaces between content elements`() {
    addModule("com.example.module1")
    addModule("com.example.module2")
    myFixture.addXmlFile("com.example.module1/com.example.module1.xml", """
      <idea-plugin visibility="public">
      </idea-plugin>
    """.trimIndent())
    myFixture.addXmlFile("com.example.module2/com.example.module2.xml", """
      <idea-plugin visibility="public">
      </idea-plugin>
    """.trimIndent())

    val testedFile = myFixture.addXmlFile("plugin.xml", """
      <idea-plugin>
        <content namespace="same-namespace">
          <module name="com.example.module1"/>
        </content>
        <content namespace="same-namespace">
          <module name="com.example.module2"/>
        </content>
      </idea-plugin>
    """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test should not report when both namespaces are empty`() {
    addModule("com.example.module1")
    addModule("com.example.module2")
    myFixture.addXmlFile("com.example.module1/com.example.module1.xml", """
      <idea-plugin visibility="private">
      </idea-plugin>
    """.trimIndent())
    myFixture.addXmlFile("com.example.module2/com.example.module2.xml", """
      <idea-plugin visibility="private">
      </idea-plugin>
    """.trimIndent())

    val testedFile = myFixture.addXmlFile("plugin.xml", """
      <idea-plugin>
        <content>
          <module name="com.example.module1"/>
        </content>
        <content>
          <module name="com.example.module2"/>
        </content>
      </idea-plugin>
    """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test mixed visibility modules in same content`() {
    addModule("com.example.privatemodule")
    addModule("com.example.publicmodule")
    myFixture.addXmlFile("com.example.privatemodule/com.example.privatemodule.xml", """
      <idea-plugin visibility="private">
      </idea-plugin>
    """.trimIndent())
    myFixture.addXmlFile("com.example.publicmodule/com.example.publicmodule.xml", """
      <idea-plugin visibility="public">
      </idea-plugin>
    """.trimIndent())

    val testedFile = myFixture.addXmlFile("plugin.xml", """
      <idea-plugin>
        <<error descr="Namespace is required for 'content' with non-private modules">content</error>>
          <module name="com.example.privatemodule"/>
          <module name="com.example.publicmodule"/>
        </content>
      </idea-plugin>
    """.trimIndent())
    testHighlighting(testedFile)
  }

  private fun addModule(moduleName: String) {
    PsiTestUtil.addModule(
      project,
      PluginModuleType.getInstance(),
      moduleName,
      myFixture.tempDirFixture.findOrCreateDir(moduleName)
    )
  }

  private fun testHighlighting(testedFile: PsiFile) {
    myFixture.testHighlighting(true, true, true, testedFile.virtualFile)
  }
}

class ContentModuleNamespaceInspectionFixTest : JavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(ContentModuleNamespaceInspection())
  }

  fun `test add namespace fix in IntelliJ project`() {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, true)
    addModule("com.example.publicmodule")
    myFixture.addXmlFile("com.example.publicmodule/com.example.publicmodule.xml", """
      <idea-plugin visibility="public">
      </idea-plugin>
    """.trimIndent())

    doTestAddNamespace(
      """
      <idea-plugin>
        <cont<caret>ent>
          <module name="com.example.publicmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent(),
      """
      <idea-plugin>
        <content namespace="jetbrains">
          <module name="com.example.publicmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent(),
      "Add 'jetbrains' namespace"
    )
  }

  fun `test add namespace fix in a regular plugin project`() {
    addModule("com.example.publicmodule")
    myFixture.addXmlFile("com.example.publicmodule/com.example.publicmodule.xml", """
      <idea-plugin visibility="public">
      </idea-plugin>
    """.trimIndent())

    doTestAddNamespace(
      """
      <idea-plugin>
        <cont<caret>ent>
          <module name="com.example.publicmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent(),
      """
      <idea-plugin>
        <content namespace="<caret>">
          <module name="com.example.publicmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent(),
      "Add namespace"
    )
  }

  private fun addModule(moduleName: String) {
    PsiTestUtil.addModule(
      project,
      PluginModuleType.getInstance(),
      moduleName,
      myFixture.tempDirFixture.findOrCreateDir(moduleName)
    )
  }

  private fun doTestAddNamespace(
    @Language("XML") before: String,
    @Language("XML") after: String,
    intentionName: String,
  ) {
    val testedFile = myFixture.addXmlFile("plugin.xml", before)
    myFixture.configureFromExistingVirtualFile(testedFile.virtualFile)
    val intention = myFixture.findSingleIntention(intentionName)
    myFixture.checkPreviewAndLaunchAction(intention)
    myFixture.checkResult(after, true)
  }
}

private fun CodeInsightTestFixture.addXmlFile(relativePath: String, @Language("XML") fileText: String): PsiFile {
  return this.addFileToProject(relativePath, fileText)
}
