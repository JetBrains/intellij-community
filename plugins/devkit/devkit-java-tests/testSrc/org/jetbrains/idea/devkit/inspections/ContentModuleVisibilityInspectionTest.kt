// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.module.PluginModuleType

class ContentModuleVisibilityInspectionTest : JavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    // it is required for correct recognizing if the project is a plugin project (see PsiUtil.IDE_PROJECT_MARKER_CLASS):
    myFixture.addClass("package com.intellij.ui.components; public class JBList {}")
    myFixture.enableInspections(ContentModuleVisibilityInspection())
  }

  fun `test should report private module dependency from internal module`() {
    addModuleWithPluginDescriptor(
      "com.example.plugin.with.privatemodule",
      "com.example.plugin.with.privatemodule/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.privatemodule</id>
        <content>
          <module name="com.example.privatemodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())
    addModuleWithPluginDescriptor(
      "com.example.privatemodule",
      "com.example.privatemodule/com.example.privatemodule.xml",
      """
      <idea-plugin visibility="private">
      </idea-plugin>
      """.trimIndent())

    addModuleWithPluginDescriptor(
      "com.example.plugin.with.internalmodule",
      "com.example.plugin.with.internalmodule/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.internalmodule</id>
        <content>
          <module name="com.example.internalmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())
    val testedFile = addModuleWithPluginDescriptor(
      "com.example.internalmodule",
      "com.example.internalmodule/com.example.internalmodule.xml",
      """
      <idea-plugin visibility="internal">
        <dependencies>
          <module name="<error descr="The 'com.example.privatemodule' module is private and declared in plugin 'com.example.plugin.with.privatemodule', so it cannot be accessed from module 'com.example.internalmodule' declared in plugin 'com.example.plugin.with.internalmodule'">com.example.privatemodule</error>"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test should report private module dependency from public module`() {
    addModuleWithPluginDescriptor(
      "com.example.plugin.with.privatemodule",
      "com.example.plugin.with.privatemodule/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.privatemodule</id>
        <content>
          <module name="com.example.privatemodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())
    addModuleWithPluginDescriptor(
      "com.example.privatemodule",
      "com.example.privatemodule/com.example.privatemodule.xml",
      """
      <idea-plugin visibility="private">
      </idea-plugin>
      """.trimIndent())

    addModuleWithPluginDescriptor(
      "com.example.plugin.with.publicmodule",
      "com.example.plugin.with.publicmodule/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.publicmodule</id>
        <content namespace="test-namespace">
          <module name="com.example.publicmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())
    val testedFile = addModuleWithPluginDescriptor(
      "com.example.publicmodule",
      "com.example.publicmodule/com.example.publicmodule.xml",
      """
      <idea-plugin visibility="public">
        <dependencies>
          <module name="<error descr="The 'com.example.privatemodule' module is private and declared in plugin 'com.example.plugin.with.privatemodule', so it cannot be accessed from module 'com.example.publicmodule' declared in plugin 'com.example.plugin.with.publicmodule'">com.example.privatemodule</error>"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test should report internal module with different namespace`() {
    addModuleWithPluginDescriptor(
      "com.example.plugin.with.internalmodule",
      "com.example.plugin.with.internalmodule/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.internalmodule</id>
        <content namespace="test-other-namespace">
          <module name="com.example.internalmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())
    addModuleWithPluginDescriptor(
      "com.example.internalmodule",
      "com.example.internalmodule/com.example.internalmodule.xml",
      """
      <idea-plugin visibility="internal">
      </idea-plugin>
      """.trimIndent())

    addModuleWithPluginDescriptor(
      "com.example.plugin.with.currentmodule",
      "com.example.plugin.with.currentmodule/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.currentmodule</id>
        <content namespace="test-namespace">
          <module name="com.example.currentmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())
    val testedFile = addModuleWithPluginDescriptor(
      "com.example.currentmodule",
      "com.example.currentmodule/com.example.currentmodule.xml",
      """
      <idea-plugin visibility="public">
        <dependencies>
          <module name="<error descr="The 'com.example.internalmodule' module is internal and declared in namespace 'test-other-namespace' in 'com.example.plugin.with.internalmodule/…/plugin.xml', so it cannot be accessed from module 'com.example.currentmodule', which is declared in namespace 'test-namespace' in 'com.example.plugin.with.currentmodule/…/plugin.xml'">com.example.internalmodule</error>"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test should report internal module without namespace`() {
    addModuleWithPluginDescriptor(
      "com.example.plugin.with.internalmodule",
      "com.example.plugin.with.internalmodule/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.internalmodule</id>
        <content>
          <module name="com.example.internalmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())
    addModuleWithPluginDescriptor(
      "com.example.internalmodule",
      "com.example.internalmodule/com.example.internalmodule.xml",
      """
      <idea-plugin visibility="internal">
      </idea-plugin>
      """.trimIndent())

    addModuleWithPluginDescriptor(
      "com.example.plugin.with.currentmodule",
      "com.example.plugin.with.currentmodule/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.currentmodule</id>
        <content namespace="test-namespace">
          <module name="com.example.currentmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())
    val testedFile = addModuleWithPluginDescriptor(
      "com.example.currentmodule",
      "com.example.currentmodule/com.example.currentmodule.xml",
      """
      <idea-plugin visibility="public">
        <dependencies>
          <module name="<error descr="The 'com.example.internalmodule' module is internal and declared without namespace in 'com.example.plugin.with.internalmodule/…/plugin.xml', so it cannot be accessed from module 'com.example.currentmodule', which is declared in namespace 'test-namespace' in 'com.example.plugin.with.currentmodule/…/plugin.xml'">com.example.internalmodule</error>"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test should report internal module when current module has no namespace`() {
    addModuleWithPluginDescriptor(
      "com.example.plugin.with.internalmodule",
      "com.example.plugin.with.internalmodule/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.internalmodule</id>
        <content namespace="test-other-namespace">
          <module name="com.example.internalmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())
    addModuleWithPluginDescriptor(
      "com.example.internalmodule",
      "com.example.internalmodule/com.example.internalmodule.xml",
      """
      <idea-plugin visibility="internal">
      </idea-plugin>
      """.trimIndent())

    addModuleWithPluginDescriptor(
      "com.example.plugin.with.currentmodule",
      "com.example.plugin.with.currentmodule/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.currentmodule</id>
        <content>
          <module name="com.example.currentmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())
    val testedFile = addModuleWithPluginDescriptor(
      "com.example.currentmodule",
      "com.example.currentmodule/com.example.currentmodule.xml",
      """
      <idea-plugin visibility="public">
        <dependencies>
          <module name="<error descr="The 'com.example.internalmodule' module is internal and declared in namespace 'test-other-namespace' in 'com.example.plugin.with.internalmodule/…/plugin.xml', so it cannot be accessed from module 'com.example.currentmodule', which is declared without namespace in 'com.example.plugin.with.currentmodule/…/plugin.xml'">com.example.internalmodule</error>"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test should report private module included via xi-include`() {
    addModuleWithPluginDescriptor(
      "com.example.plugin.with.privatemodule",
      "com.example.plugin.with.privatemodule/META-INF/plugin.xml",
      """
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <id>com.example.plugin.with.privatemodule</id>
        <xi:include href="/META-INF/privatemodules.xml"/>
      </idea-plugin>
      """.trimIndent())

    addXmlFile(
      "com.example.plugin.with.privatemodule/META-INF/privatemodules.xml",
      """
      <idea-plugin>
        <content>
          <module name="com.example.privatemodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())

    addModuleWithPluginDescriptor(
      "com.example.privatemodule",
      "com.example.privatemodule/com.example.privatemodule.xml",
      """
      <idea-plugin visibility="private">
      </idea-plugin>
      """.trimIndent())

    addModuleWithPluginDescriptor(
      "com.example.plugin.with.publicmodule",
      "com.example.plugin.with.publicmodule/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.publicmodule</id>
        <content>
          <module name="com.example.publicmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())

    val testedFile = addModuleWithPluginDescriptor(
      "com.example.publicmodule",
      "com.example.publicmodule/com.example.publicmodule.xml",
      """
      <idea-plugin visibility="public">
        <dependencies>
          <module name="<error descr="The 'com.example.privatemodule' module is private and declared in plugin 'com.example.plugin.with.privatemodule', so it cannot be accessed from module 'com.example.publicmodule' declared in plugin 'com.example.plugin.with.publicmodule'">com.example.privatemodule</error>"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent())

    testHighlighting(testedFile)
  }

  fun `test should report private module included via multiple xi-includes`() {
    addModuleWithPluginDescriptor(
      "com.example.plugin.with.privatemodule",
      "com.example.plugin.with.privatemodule/META-INF/plugin.xml",
      """
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <id>com.example.plugin.with.privatemodule</id>
        <xi:include href="/META-INF/modules.xml"/>
      </idea-plugin>
      """.trimIndent())

    addXmlFile(
      "com.example.plugin.with.privatemodule/META-INF/modules.xml",
      """
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <xi:include href="/META-INF/privatemodules.xml"/>
      </idea-plugin>
      """.trimIndent())

    addXmlFile(
      "com.example.plugin.with.privatemodule/META-INF/privatemodules.xml",
      """
      <idea-plugin>
        <content>
          <module name="com.example.privatemodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())

    addModuleWithPluginDescriptor(
      "com.example.privatemodule",
      "com.example.privatemodule/com.example.privatemodule.xml",
      """
      <idea-plugin visibility="private">
      </idea-plugin>
      """.trimIndent())

    addModuleWithPluginDescriptor(
      "com.example.plugin.with.publicmodule",
      "com.example.plugin.with.publicmodule/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.publicmodule</id>
        <content>
          <module name="com.example.publicmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())

    val testedFile = addModuleWithPluginDescriptor(
      "com.example.publicmodule",
      "com.example.publicmodule/com.example.publicmodule.xml",
      """
      <idea-plugin visibility="public">
        <dependencies>
          <module name="<error descr="The 'com.example.privatemodule' module is private and declared in plugin 'com.example.plugin.with.privatemodule', so it cannot be accessed from module 'com.example.publicmodule' declared in plugin 'com.example.plugin.with.publicmodule'">com.example.privatemodule</error>"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent())

    testHighlighting(testedFile)
  }

  fun `test should not report public module`() {
    addModuleWithPluginDescriptor(
      "com.example.plugin.with.publicmodule",
      "com.example.plugin.with.publicmodule/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.publicmodule</id>
        <content>
          <module name="com.example.publicmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())
    addModuleWithPluginDescriptor(
      "com.example.publicmodule",
      "com.example.publicmodule/com.example.publicmodule.xml",
      """
      <idea-plugin visibility="public">
      </idea-plugin>
      """.trimIndent())

    addModuleWithPluginDescriptor(
      "com.example.plugin.with.currentmodule",
      "com.example.plugin.with.currentmodule/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.currentmodule</id>
        <content namespace="test-namespace">
          <module name="com.example.currentmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())
    val testedFile = addModuleWithPluginDescriptor(
      "com.example.currentmodule",
      "com.example.currentmodule/com.example.currentmodule.xml",
      """
      <idea-plugin>
        <dependencies>
          <module name="com.example.publicmodule"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test should not report internal module with same namespace`() {
    addModuleWithPluginDescriptor(
      "com.example.plugin.with.internalmodule",
      "com.example.plugin.with.internalmodule/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.internalmodule</id>
        <content namespace="test-namespace">
          <module name="com.example.internalmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())
    addModuleWithPluginDescriptor(
      "com.example.internalmodule",
      "com.example.internalmodule/com.example.internalmodule.xml",
      """
      <idea-plugin visibility="internal">
      </idea-plugin>
      """.trimIndent())

    addModuleWithPluginDescriptor(
      "com.example.plugin.with.currentmodule",
      "com.example.plugin.with.currentmodule/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.currentmodule</id>
        <content namespace="test-namespace">
          <module name="com.example.currentmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())
    val testedFile = addModuleWithPluginDescriptor(
      "com.example.currentmodule",
      "com.example.currentmodule/com.example.currentmodule.xml",
      """
      <idea-plugin>
        <dependencies>
          <module name="com.example.internalmodule"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test should not report private module in same plugin`() {
    addModuleWithPluginDescriptor(
      "com.example.plugin",
      "com.example.plugin/META-INF/plugin.xml",
      """
      <idea-plugin>
        <content namespace="test-namespace">
          <module name="com.example.privatemodule"/>
          <module name="com.example.anothermodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())

    addModuleWithPluginDescriptor(
      "com.example.privatemodule",
      "com.example.privatemodule/com.example.privatemodule.xml",
      """
      <idea-plugin visibility="private">
      </idea-plugin>
      """.trimIndent())

    val testedFile = addModuleWithPluginDescriptor(
      "com.example.anothermodule",
      "com.example.anothermodule/com.example.anothermodule.xml",
      """
      <idea-plugin visibility="public">
        <dependencies>
          <module name="com.example.privatemodule"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test should not report when both modules included in same plugin`() {
    addModuleWithPluginDescriptor(
      "com.example.plugin",
      "com.example.plugin/META-INF/plugin.xml",
      """
      <idea-plugin>
        <content>
          <module name="com.example.internalmodule"/>
          <module name="com.example.currentmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())

    addModuleWithPluginDescriptor(
      "com.example.internalmodule",
      "com.example.internalmodule/com.example.internalmodule.xml",
      """
      <idea-plugin visibility="internal">
      </idea-plugin>
      """.trimIndent())

    val testedFile = addModuleWithPluginDescriptor(
      "com.example.currentmodule",
      "com.example.currentmodule/com.example.currentmodule.xml",
      """
      <idea-plugin visibility="public">
        <dependencies>
          <module name="com.example.internalmodule"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test should not report private module included via xi-include`() {
    addModuleWithPluginDescriptor(
      "com.example.plugin.with.privatemodule",
      "com.example.plugin.with.privatemodule/META-INF/plugin.xml",
      """
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <id>com.example.plugin.with.privatemodule</id>
        <content>
          <module name="com.example.publicmodule"/>
        </content>
        <xi:include href="/META-INF/privatemodules.xml"/>
      </idea-plugin>
      """.trimIndent())

    addXmlFile(
      "com.example.plugin.with.privatemodule/META-INF/privatemodules.xml",
      """
      <idea-plugin>
        <content>
          <module name="com.example.privatemodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())

    addModuleWithPluginDescriptor(
      "com.example.privatemodule",
      "com.example.privatemodule/com.example.privatemodule.xml",
      """
      <idea-plugin visibility="private">
      </idea-plugin>
      """.trimIndent())

    val testedFile = addModuleWithPluginDescriptor(
      "com.example.publicmodule",
      "com.example.publicmodule/com.example.publicmodule.xml",
      """
      <idea-plugin visibility="public">
        <dependencies>
          <module name="com.example.privatemodule"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent())

    testHighlighting(testedFile)
  }

  fun `test should not report private module included via multiple xi-includes`() {
    addModuleWithPluginDescriptor(
      "com.example.plugin.with.privatemodule",
      "com.example.plugin.with.privatemodule/META-INF/plugin.xml",
      """
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <id>com.example.plugin.with.privatemodule</id>
        <content>
          <module name="com.example.publicmodule"/>
        </content>
        <xi:include href="/META-INF/modules.xml"/>
      </idea-plugin>
      """.trimIndent())

    addXmlFile(
      "com.example.plugin.with.privatemodule/META-INF/modules.xml",
      """
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <xi:include href="/META-INF/privatemodules.xml"/>
      </idea-plugin>
      """.trimIndent())

    addXmlFile(
      "com.example.plugin.with.privatemodule/META-INF/privatemodules.xml",
      """
      <idea-plugin>
        <content>
          <module name="com.example.privatemodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())

    addModuleWithPluginDescriptor(
      "com.example.privatemodule",
      "com.example.privatemodule/com.example.privatemodule.xml",
      """
      <idea-plugin visibility="private">
      </idea-plugin>
      """.trimIndent())

    val testedFile = addModuleWithPluginDescriptor(
      "com.example.publicmodule",
      "com.example.publicmodule/com.example.publicmodule.xml",
      """
      <idea-plugin visibility="public">
        <dependencies>
          <module name="com.example.privatemodule"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent())

    testHighlighting(testedFile)
  }

  private fun addModuleWithPluginDescriptor(
    moduleName: String,
    pluginDescriptorFilePath: String,
    @Language("XML") pluginDescriptorContent: String,
  ): PsiFile {
    PsiTestUtil.addModule(project, PluginModuleType.getInstance(), moduleName, myFixture.tempDirFixture.findOrCreateDir(moduleName))
    return addXmlFile(pluginDescriptorFilePath, pluginDescriptorContent)
  }

  private fun addXmlFile(relativePath: String, @Language("XML") fileText: String): PsiFile {
    return myFixture.addFileToProject(relativePath, fileText)
  }

  private fun testHighlighting(testedFile: PsiFile) {
    myFixture.testHighlighting(true, true, true, testedFile.virtualFile)
  }
}
