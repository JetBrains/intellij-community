// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.module.JavaModuleType
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language

internal class AllContentModulesOptionalLoadingRuleInspectionTest : JavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(AllContentModulesOptionalLoadingRuleInspection::class.java)
  }

  private fun addContentModules(vararg names: String) {
    for (name in names) {
      PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), name, myFixture.tempDirFixture.findOrCreateDir(name))
      myFixture.addFileToProject("$name/$name.xml", "<idea-plugin/>")
    }
  }

  fun `test warning when all modules have no loading attribute`() {
    addContentModules("module1", "module2")
    val pluginXml = addPluginXmlToProject("""
      <idea-plugin>
        <id>com.example.plugin</id>
        <<warning descr="All content modules use the 'optional' loading rule. At least one module should use 'required', 'embedded', or 'required-if-available'.">content</warning>>
          <module name="module1"/>
          <module name="module2"/>
        </content>
      </idea-plugin>
    """.trimIndent())
    myFixture.testHighlighting(true, true, true, pluginXml.virtualFile)
  }

  fun `test warning when module has explicit loading optional`() {
    addContentModules("module1")
    val pluginXml = addPluginXmlToProject("""
      <idea-plugin>
        <id>com.example.plugin</id>
        <<warning descr="All content modules use the 'optional' loading rule. At least one module should use 'required', 'embedded', or 'required-if-available'.">content</warning>>
          <module name="module1" loading="optional"/>
        </content>
      </idea-plugin>
    """.trimIndent())
    myFixture.testHighlighting(true, true, true, pluginXml.virtualFile)
  }

  fun `test warning when mix of default optional and on-demand`() {
    addContentModules("module1", "module2")
    val pluginXml = addPluginXmlToProject("""
      <idea-plugin>
        <id>com.example.plugin</id>
        <<warning descr="All content modules use the 'optional' loading rule. At least one module should use 'required', 'embedded', or 'required-if-available'.">content</warning>>
          <module name="module1"/>
          <module name="module2" loading="on-demand"/>
        </content>
      </idea-plugin>
    """.trimIndent())
    myFixture.testHighlighting(true, true, true, pluginXml.virtualFile)
  }

  fun `test no warning when one module has loading required`() {
    addContentModules("module1", "module2")
    val pluginXml = addPluginXmlToProject("""
      <idea-plugin>
        <id>com.example.plugin</id>
        <content>
          <module name="module1"/>
          <module name="module2" loading="required"/>
        </content>
      </idea-plugin>
    """.trimIndent())
    myFixture.testHighlighting(true, true, true, pluginXml.virtualFile)
  }

  fun `test no warning when one module has loading embedded`() {
    addContentModules("module1")
    val pluginXml = addPluginXmlToProject("""
      <idea-plugin>
        <id>com.example.plugin</id>
        <content>
          <module name="module1" loading="embedded"/>
        </content>
      </idea-plugin>
    """.trimIndent())
    myFixture.testHighlighting(true, true, true, pluginXml.virtualFile)
  }

  fun `test no warning when one module has required-if-available`() {
    addContentModules("module1")
    val pluginXml = addPluginXmlToProject("""
      <idea-plugin>
        <id>com.example.plugin</id>
        <content>
          <module name="module1" required-if-available="com.example.other"/>
        </content>
      </idea-plugin>
    """.trimIndent())
    myFixture.testHighlighting(true, true, true, pluginXml.virtualFile)
  }

  fun `test no warning when no content modules`() {
    val pluginXml = addPluginXmlToProject("""
      <idea-plugin>
        <id>com.example.plugin</id>
      </idea-plugin>
    """.trimIndent())
    myFixture.testHighlighting(true, true, true, pluginXml.virtualFile)
  }

  fun `test no warning for content module descriptor without id`() {
    addContentModules("module1")
    val pluginXml = addPluginXmlToProject("""
      <idea-plugin>
        <content>
          <module name="module1"/>
        </content>
      </idea-plugin>
    """.trimIndent())
    myFixture.testHighlighting(true, true, true, pluginXml.virtualFile)
  }

  fun `test warning with multiple content elements all optional`() {
    addContentModules("module1", "module2")
    val pluginXml = addPluginXmlToProject("""
      <idea-plugin>
        <id>com.example.plugin</id>
        <<warning descr="All content modules use the 'optional' loading rule. At least one module should use 'required', 'embedded', or 'required-if-available'.">content</warning>>
          <module name="module1"/>
        </content>
        <content>
          <module name="module2" loading="optional"/>
        </content>
      </idea-plugin>
    """.trimIndent())
    myFixture.testHighlighting(true, true, true, pluginXml.virtualFile)
  }

  fun `test no warning with multiple content elements one required in second`() {
    addContentModules("module1", "module2")
    val pluginXml = addPluginXmlToProject("""
      <idea-plugin>
        <id>com.example.plugin</id>
        <content>
          <module name="module1"/>
        </content>
        <content>
          <module name="module2" loading="required"/>
        </content>
      </idea-plugin>
    """.trimIndent())
    myFixture.testHighlighting(true, true, true, pluginXml.virtualFile)
  }
  
  fun `test no warning when extension points are registered directly`() {
    addContentModules("module1")
    val pluginXml = addPluginXmlToProject("""
      <idea-plugin>
        <id>com.example.plugin</id>
        <content>
          <module name="module1"/>
        </content>
        <extensionPoints>
          <extensionPoint name="com.example.ep" interface="com.example.MyEp"/>
        </extensionPoints>
      </idea-plugin>
    """.trimIndent())
    myFixture.testHighlighting(true, true, true, pluginXml.virtualFile)
  }

  fun `test no warning when extensions are registered directly`() {
    addContentModules("module1")
    val pluginXml = addPluginXmlToProject("""
      <idea-plugin>
        <id>com.example.plugin</id>
        <content>
          <module name="module1"/>
        </content>
        <extensions defaultExtensionNs="com.intellij">
          <registeredExtension/>
        </extensions>
      </idea-plugin>
    """.trimIndent())
    myFixture.testHighlighting(true, true, true, pluginXml.virtualFile)
  }

  fun `test no warning when application listeners are registered directly`() {
    addContentModules("module1")
    val pluginXml = addPluginXmlToProject("""
      <idea-plugin>
        <id>com.example.plugin</id>
        <content>
          <module name="module1"/>
        </content>
        <applicationListeners>
          <listener class="com.example.MyListener" topic="com.example.MyTopic"/>
        </applicationListeners>
      </idea-plugin>
    """.trimIndent())
    myFixture.testHighlighting(true, true, true, pluginXml.virtualFile)
  }

  fun `test no warning when project listeners are registered directly`() {
    addContentModules("module1")
    val pluginXml = addPluginXmlToProject("""
      <idea-plugin>
        <id>com.example.plugin</id>
        <content>
          <module name="module1"/>
        </content>
        <projectListeners>
          <listener class="com.example.MyListener" topic="com.example.MyTopic"/>
        </projectListeners>
      </idea-plugin>
    """.trimIndent())
    myFixture.testHighlighting(true, true, true, pluginXml.virtualFile)
  }

  fun `test no warning when actions are registered directly`() {
    addContentModules("module1")
    val pluginXml = addPluginXmlToProject("""
      <idea-plugin>
        <id>com.example.plugin</id>
        <content>
          <module name="module1"/>
        </content>
        <actions>
          <action id="com.example.MyAction" class="com.example.MyAction"/>
        </actions>
      </idea-plugin>
    """.trimIndent())
    myFixture.testHighlighting(true, true, true, pluginXml.virtualFile)
  }

  fun `test warning when extensionPoints tag is empty`() {
    addContentModules("module1")
    val pluginXml = addPluginXmlToProject("""
      <idea-plugin>
        <id>com.example.plugin</id>
        <<warning descr="All content modules use the 'optional' loading rule. At least one module should use 'required', 'embedded', or 'required-if-available'.">content</warning>>
          <module name="module1"/>
        </content>
        <extensionPoints></extensionPoints>
      </idea-plugin>
    """.trimIndent())
    myFixture.testHighlighting(true, true, true, pluginXml.virtualFile)
  }

  fun `test warning when extensions tag is empty`() {
    addContentModules("module1")
    val pluginXml = addPluginXmlToProject("""
      <idea-plugin>
        <id>com.example.plugin</id>
        <<warning descr="All content modules use the 'optional' loading rule. At least one module should use 'required', 'embedded', or 'required-if-available'.">content</warning>>
          <module name="module1"/>
        </content>
        <extensions defaultExtensionNs="com.intellij"></extensions>
      </idea-plugin>
    """.trimIndent())
    myFixture.testHighlighting(true, true, true, pluginXml.virtualFile)
  }

  fun `test warning when applicationListeners tag is empty`() {
    addContentModules("module1")
    val pluginXml = addPluginXmlToProject("""
      <idea-plugin>
        <id>com.example.plugin</id>
        <<warning descr="All content modules use the 'optional' loading rule. At least one module should use 'required', 'embedded', or 'required-if-available'.">content</warning>>
          <module name="module1"/>
        </content>
        <applicationListeners></applicationListeners>
      </idea-plugin>
    """.trimIndent())
    myFixture.testHighlighting(true, true, true, pluginXml.virtualFile)
  }

  fun `test warning when projectListeners tag is empty`() {
    addContentModules("module1")
    val pluginXml = addPluginXmlToProject(
      """
      <idea-plugin>
        <id>com.example.plugin</id>
        <<warning descr="All content modules use the 'optional' loading rule. At least one module should use 'required', 'embedded', or 'required-if-available'.">content</warning>>
          <module name="module1"/>
        </content>
        <projectListeners></projectListeners>
      </idea-plugin>
    """.trimIndent())
    myFixture.testHighlighting(true, true, true, pluginXml.virtualFile)
  }

  fun `test warning when actions tag is empty`() {
    addContentModules("module1")
    val pluginXml = addPluginXmlToProject(
      """
      <idea-plugin>
        <id>com.example.plugin</id>
        <<warning descr="All content modules use the 'optional' loading rule. At least one module should use 'required', 'embedded', or 'required-if-available'.">content</warning>>
          <module name="module1"/>
        </content>
        <actions></actions>
      </idea-plugin>
    """.trimIndent())
    myFixture.testHighlighting(true, true, true, pluginXml.virtualFile)
  }

  fun `test no warning when extensions are registered in xiIncluded file`() {
    addContentModules("module1")
    myFixture.addFileToProject("included.xml", """
      <extensions defaultExtensionNs="com.intellij">
        <registeredExtension/>
      </extensions>
    """.trimIndent())
    val pluginXml = addPluginXmlToProject("""
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <id>com.example.plugin</id>
        <content>
          <module name="module1"/>
        </content>
        <xi:include href="included.xml"/>
      </idea-plugin>
    """.trimIndent())
    myFixture.testHighlighting(true, false, true, pluginXml.virtualFile)
  }

  fun `test warning when xiIncluded file has only empty registration tags`() {
    addContentModules("module1")
    myFixture.addFileToProject("included.xml", """
      <extensionPoints/>
    """.trimIndent())
    val pluginXml = addPluginXmlToProject("""
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <id>com.example.plugin</id>
        <<warning descr="All content modules use the 'optional' loading rule. At least one module should use 'required', 'embedded', or 'required-if-available'.">content</warning>>
          <module name="module1"/>
        </content>
        <xi:include href="included.xml"/>
      </idea-plugin>
    """.trimIndent())
    myFixture.testHighlighting(true, false, true, pluginXml.virtualFile)
  }

  private fun addPluginXmlToProject(@Language("XML") code: String): PsiFile {
    return myFixture.addFileToProject("plugin.xml", code)
  }
}
