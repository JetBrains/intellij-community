// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.module.PluginModuleType

abstract class ContentModuleVisibilityInspectionTestBase : JavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    // it is required for correct recognizing if the project is a plugin project (see PsiUtil.IDE_PROJECT_MARKER_CLASS):
    myFixture.addClass("package com.intellij.ui.components; public class JBList {}")
    myFixture.enableInspections(ContentModuleVisibilityInspection())
  }
}

class ContentModuleVisibilityInspectionTest : ContentModuleVisibilityInspectionTestBase() {

  fun `test should report private module dependency from internal module`() {
    myFixture.addModuleWithPluginDescriptor(
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
    myFixture.addModuleWithPluginDescriptor(
      "com.example.privatemodule",
      "com.example.privatemodule/com.example.privatemodule.xml",
      """
      <idea-plugin visibility="private">
      </idea-plugin>
      """.trimIndent())

    myFixture.addModuleWithPluginDescriptor(
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
    val testedFile = myFixture.addModuleWithPluginDescriptor(
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
    myFixture.addModuleWithPluginDescriptor(
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
    myFixture.addModuleWithPluginDescriptor(
      "com.example.privatemodule",
      "com.example.privatemodule/com.example.privatemodule.xml",
      """
      <idea-plugin visibility="private">
      </idea-plugin>
      """.trimIndent())

    myFixture.addModuleWithPluginDescriptor(
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
    val testedFile = myFixture.addModuleWithPluginDescriptor(
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

  fun `test should report private module dependency when current module is included in root plugin with non-plugin-xml name`() {
    myFixture.addModuleWithPluginDescriptor(
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
    myFixture.addModuleWithPluginDescriptor(
      "com.example.privatemodule",
      "com.example.privatemodule/com.example.privatemodule.xml",
      """
      <idea-plugin visibility="private">
      </idea-plugin>
      """.trimIndent())

    myFixture.addModuleWithPluginDescriptor(
      "com.example.plugin.with.publicmodule",
      "com.example.plugin.with.publicmodule/META-INF/WebStormPlugin.xml",
      """
      <idea-plugin>
        <content namespace="test-namespace">
          <module name="com.example.publicmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())
    val testedFile = myFixture.addModuleWithPluginDescriptor(
      "com.example.publicmodule",
      "com.example.publicmodule/com.example.publicmodule.xml",
      """
      <idea-plugin visibility="public">
        <dependencies>
          <module name="<error descr="The 'com.example.privatemodule' module is private and declared in plugin 'com.example.plugin.with.privatemodule', so it cannot be accessed from module 'com.example.publicmodule' declared in plugin 'WebStormPlugin.xml'">com.example.privatemodule</error>"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test should report internal module with different namespace`() {
    myFixture.addModuleWithPluginDescriptor(
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
    myFixture.addModuleWithPluginDescriptor(
      "com.example.internalmodule",
      "com.example.internalmodule/com.example.internalmodule.xml",
      """
      <idea-plugin visibility="internal">
      </idea-plugin>
      """.trimIndent())

    myFixture.addModuleWithPluginDescriptor(
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
    val testedFile = myFixture.addModuleWithPluginDescriptor(
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

  fun `test should report internal module with different namespace when current module is included in root plugin with non-plugin-xml name`() {
    myFixture.addModuleWithPluginDescriptor(
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
    myFixture.addModuleWithPluginDescriptor(
      "com.example.internalmodule",
      "com.example.internalmodule/com.example.internalmodule.xml",
      """
      <idea-plugin visibility="internal">
      </idea-plugin>
      """.trimIndent())

    myFixture.addModuleWithPluginDescriptor(
      "com.example.plugin.with.currentmodule",
      "com.example.plugin.with.currentmodule/META-INF/GoLandPlugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.currentmodule</id>
        <content namespace="test-namespace">
          <module name="com.example.currentmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())
    val testedFile = myFixture.addModuleWithPluginDescriptor(
      "com.example.currentmodule",
      "com.example.currentmodule/com.example.currentmodule.xml",
      """
      <idea-plugin visibility="public">
        <dependencies>
          <module name="<error descr="The 'com.example.internalmodule' module is internal and declared in namespace 'test-other-namespace' in 'plugin.xml', so it cannot be accessed from module 'com.example.currentmodule', which is declared in namespace 'test-namespace' in 'GoLandPlugin.xml'">com.example.internalmodule</error>"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test should report internal module without namespace`() {
    myFixture.addModuleWithPluginDescriptor(
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
    myFixture.addModuleWithPluginDescriptor(
      "com.example.internalmodule",
      "com.example.internalmodule/com.example.internalmodule.xml",
      """
      <idea-plugin visibility="internal">
      </idea-plugin>
      """.trimIndent())

    myFixture.addModuleWithPluginDescriptor(
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
    val testedFile = myFixture.addModuleWithPluginDescriptor(
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
    myFixture.addModuleWithPluginDescriptor(
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
    myFixture.addModuleWithPluginDescriptor(
      "com.example.internalmodule",
      "com.example.internalmodule/com.example.internalmodule.xml",
      """
      <idea-plugin visibility="internal">
      </idea-plugin>
      """.trimIndent())

    myFixture.addModuleWithPluginDescriptor(
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
    val testedFile = myFixture.addModuleWithPluginDescriptor(
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

  fun `test should report internal module dependency directly from plugin`() {
    myFixture.addModuleWithPluginDescriptor(
      "com.example.plugin.with.internalmodule",
      "com.example.plugin.with.internalmodule/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.internalmodule</id>
        <content namespace="another-namespace">
          <module name="com.example.internalmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())
    myFixture.addModuleWithPluginDescriptor(
      "com.example.internalmodule",
      "com.example.internalmodule/com.example.internalmodule.xml",
      """
      <idea-plugin visibility="internal">
      </idea-plugin>
      """.trimIndent())

    val testedFile = myFixture.addModuleWithPluginDescriptor(
      "com.example.plugin",
      "com.example.plugin/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin</id>
        <dependencies>
          <module name="<error descr="The 'com.example.internalmodule' module is internal and declared in namespace 'another-namespace' in 'com.example.plugin.with.internalmodule/…/plugin.xml', so it cannot be accessed from plugin 'com.example.plugin', which is declared in namespace 'test-namespace'">com.example.internalmodule</error>"/>
        </dependencies>
        <content namespace="test-namespace">
          <!-- to register namespace for the plugin -->
        </content>
      </idea-plugin>
      """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test should report internal module dependency directly from plugin when current plugin is declared without namespace`() {
    myFixture.addModuleWithPluginDescriptor(
      "com.example.plugin.with.internalmodule",
      "com.example.plugin.with.internalmodule/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.internalmodule</id>
        <content namespace="another-namespace">
          <module name="com.example.internalmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())
    myFixture.addModuleWithPluginDescriptor(
      "com.example.internalmodule",
      "com.example.internalmodule/com.example.internalmodule.xml",
      """
      <idea-plugin visibility="internal">
      </idea-plugin>
      """.trimIndent())

    val testedFile = myFixture.addModuleWithPluginDescriptor(
      "com.example.plugin",
      "com.example.plugin/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin</id>
        <dependencies>
          <module name="<error descr="The 'com.example.internalmodule' module is internal and declared in namespace 'another-namespace' in 'com.example.plugin.with.internalmodule/…/plugin.xml', so it cannot be accessed from plugin 'com.example.plugin', which is declared without a namespace">com.example.internalmodule</error>"/>
        </dependencies>
        <!-- no namespace -->
      </idea-plugin>
      """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test should report internal module dependency directly from plugin when dependency declared without namespace`() {
    myFixture.addModuleWithPluginDescriptor(
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
    myFixture.addModuleWithPluginDescriptor(
      "com.example.internalmodule",
      "com.example.internalmodule/com.example.internalmodule.xml",
      """
      <idea-plugin visibility="internal">
      </idea-plugin>
      """.trimIndent())

    val testedFile = myFixture.addModuleWithPluginDescriptor(
      "com.example.plugin",
      "com.example.plugin/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin</id>
        <dependencies>
          <module name="<error descr="The 'com.example.internalmodule' module is internal and declared without namespace in 'com.example.plugin.with.internalmodule/…/plugin.xml', so it cannot be accessed from plugin 'com.example.plugin', which is declared in namespace 'test-namespace'">com.example.internalmodule</error>"/>
        </dependencies>
        <content namespace="test-namespace">
          <!-- to register namespace for the plugin -->
        </content>
      </idea-plugin>
      """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test should report private module included via xi-include`() {
    myFixture.addModuleWithPluginDescriptor(
      "com.example.plugin.with.privatemodule",
      "com.example.plugin.with.privatemodule/META-INF/plugin.xml",
      """
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <id>com.example.plugin.with.privatemodule</id>
        <xi:include href="/META-INF/privatemodules.xml"/>
      </idea-plugin>
      """.trimIndent())

    myFixture.addXmlFile(
      "com.example.plugin.with.privatemodule/META-INF/privatemodules.xml",
      """
      <idea-plugin>
        <content>
          <module name="com.example.privatemodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())

    myFixture.addModuleWithPluginDescriptor(
      "com.example.privatemodule",
      "com.example.privatemodule/com.example.privatemodule.xml",
      """
      <idea-plugin visibility="private">
      </idea-plugin>
      """.trimIndent())

    myFixture.addModuleWithPluginDescriptor(
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

    val testedFile = myFixture.addModuleWithPluginDescriptor(
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
    myFixture.addModuleWithPluginDescriptor(
      "com.example.plugin.with.privatemodule",
      "com.example.plugin.with.privatemodule/META-INF/plugin.xml",
      """
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <id>com.example.plugin.with.privatemodule</id>
        <xi:include href="/META-INF/modules.xml"/>
      </idea-plugin>
      """.trimIndent())

    myFixture.addXmlFile(
      "com.example.plugin.with.privatemodule/META-INF/modules.xml",
      """
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <xi:include href="/META-INF/privatemodules.xml"/>
      </idea-plugin>
      """.trimIndent())

    myFixture.addXmlFile(
      "com.example.plugin.with.privatemodule/META-INF/privatemodules.xml",
      """
      <idea-plugin>
        <content>
          <module name="com.example.privatemodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())

    myFixture.addModuleWithPluginDescriptor(
      "com.example.privatemodule",
      "com.example.privatemodule/com.example.privatemodule.xml",
      """
      <idea-plugin visibility="private">
      </idea-plugin>
      """.trimIndent())

    myFixture.addModuleWithPluginDescriptor(
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

    val testedFile = myFixture.addModuleWithPluginDescriptor(
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

  fun `test should report private module dependency directly from plugin`() {
    myFixture.addModuleWithPluginDescriptor(
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
    myFixture.addModuleWithPluginDescriptor(
      "com.example.privatemodule",
      "com.example.privatemodule/com.example.privatemodule.xml",
      """
      <idea-plugin>
      </idea-plugin>
      """.trimIndent())

    val testedFile = myFixture.addModuleWithPluginDescriptor(
      "com.example.plugin",
      "com.example.plugin/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin</id>
        <dependencies>
          <module name="<error descr="The 'com.example.privatemodule' module is private and declared in plugin 'com.example.plugin.with.privatemodule', so it cannot be accessed from plugin 'com.example.plugin'">com.example.privatemodule</error>"/>
        </dependencies>
        <content namespace="test-namespace">
          <!-- to register namespace for the plugin -->
        </content>
      </idea-plugin>
      """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test should not report public module`() {
    myFixture.addModuleWithPluginDescriptor(
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
    myFixture.addModuleWithPluginDescriptor(
      "com.example.publicmodule",
      "com.example.publicmodule/com.example.publicmodule.xml",
      """
      <idea-plugin visibility="public">
      </idea-plugin>
      """.trimIndent())

    myFixture.addModuleWithPluginDescriptor(
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
    val testedFile = myFixture.addModuleWithPluginDescriptor(
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
    myFixture.addModuleWithPluginDescriptor(
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
    myFixture.addModuleWithPluginDescriptor(
      "com.example.internalmodule",
      "com.example.internalmodule/com.example.internalmodule.xml",
      """
      <idea-plugin visibility="internal">
      </idea-plugin>
      """.trimIndent())

    myFixture.addModuleWithPluginDescriptor(
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
    val testedFile = myFixture.addModuleWithPluginDescriptor(
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

  fun `test should not report internal module dependency directly from plugin`() {
    myFixture.addModuleWithPluginDescriptor(
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
    myFixture.addModuleWithPluginDescriptor(
      "com.example.internalmodule",
      "com.example.internalmodule/com.example.internalmodule.xml",
      """
      <idea-plugin visibility="internal">
      </idea-plugin>
      """.trimIndent())

    val testedFile = myFixture.addModuleWithPluginDescriptor(
      "com.example.plugin",
      "com.example.plugin/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin</id>
        <dependencies>
          <module name="com.example.internalmodule"/>
        </dependencies>
        <content namespace="test-namespace">
          <!-- to register namespace for the plugin -->
        </content>
      </idea-plugin>
      """.trimIndent())
    testHighlighting(testedFile)
  }

  fun `test should not report private module in same plugin`() {
    myFixture.addModuleWithPluginDescriptor(
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

    myFixture.addModuleWithPluginDescriptor(
      "com.example.privatemodule",
      "com.example.privatemodule/com.example.privatemodule.xml",
      """
      <idea-plugin visibility="private">
      </idea-plugin>
      """.trimIndent())

    val testedFile = myFixture.addModuleWithPluginDescriptor(
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
    myFixture.addModuleWithPluginDescriptor(
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

    myFixture.addModuleWithPluginDescriptor(
      "com.example.internalmodule",
      "com.example.internalmodule/com.example.internalmodule.xml",
      """
      <idea-plugin visibility="internal">
      </idea-plugin>
      """.trimIndent())

    val testedFile = myFixture.addModuleWithPluginDescriptor(
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
    myFixture.addModuleWithPluginDescriptor(
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

    myFixture.addXmlFile(
      "com.example.plugin.with.privatemodule/META-INF/privatemodules.xml",
      """
      <idea-plugin>
        <content>
          <module name="com.example.privatemodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())

    myFixture.addModuleWithPluginDescriptor(
      "com.example.privatemodule",
      "com.example.privatemodule/com.example.privatemodule.xml",
      """
      <idea-plugin visibility="private">
      </idea-plugin>
      """.trimIndent())

    val testedFile = myFixture.addModuleWithPluginDescriptor(
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

  fun `test should not report private module when both modules included via xi-include`() {
    myFixture.addModuleWithPluginDescriptor(
      "com.example.plugin.with.privatemodule",
      "com.example.plugin.with.privatemodule/META-INF/plugin.xml",
      """
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <id>com.example.plugin.with.privatemodule</id>
        <xi:include href="/META-INF/modules.xml"/>
      </idea-plugin>
      """.trimIndent())

    myFixture.addXmlFile(
      "com.example.plugin.with.privatemodule/META-INF/modules.xml",
      """
      <idea-plugin>
        <content>
          <module name="com.example.publicmodule"/>
          <module name="com.example.privatemodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())

    myFixture.addModuleWithPluginDescriptor(
      "com.example.privatemodule",
      "com.example.privatemodule/com.example.privatemodule.xml",
      """
      <idea-plugin visibility="private">
      </idea-plugin>
      """.trimIndent())

    val testedFile = myFixture.addModuleWithPluginDescriptor(
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
    myFixture.addModuleWithPluginDescriptor(
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

    myFixture.addXmlFile(
      "com.example.plugin.with.privatemodule/META-INF/modules.xml",
      """
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <xi:include href="/META-INF/privatemodules.xml"/>
      </idea-plugin>
      """.trimIndent())

    myFixture.addXmlFile(
      "com.example.plugin.with.privatemodule/META-INF/privatemodules.xml",
      """
      <idea-plugin>
        <content>
          <module name="com.example.privatemodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())

    myFixture.addModuleWithPluginDescriptor(
      "com.example.privatemodule",
      "com.example.privatemodule/com.example.privatemodule.xml",
      """
      <idea-plugin visibility="private">
      </idea-plugin>
      """.trimIndent())

    val testedFile = myFixture.addModuleWithPluginDescriptor(
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

  fun `test should report module dependency that is from different namespace in sources, but from the same namespace in a library`() {
    // this module is duplicated by the library added with PsiTestUtil.addLibrary below
    // (namespace in the library is test-namespace, as in com.example.plugin, so no issue would be reported, if the lib descriptor was taken)
    myFixture.addModuleWithPluginDescriptor(
      "com.example.plugin.with.internalmodule",
      "com.example.plugin.with.internalmodule/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.internalmodule</id>
        <content namespace="another-namespace">
          <module name="com.example.internalmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())

    myFixture.addModuleWithPluginDescriptor(
      "com.example.internalmodule",
      "com.example.internalmodule/com.example.internalmodule.xml",
      """
      <idea-plugin visibility="internal">
      </idea-plugin>
      """.trimIndent())

    myFixture.addModuleWithPluginDescriptor(
      "com.example.plugin",
      "com.example.plugin/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin</id>
        <content namespace="test-namespace">
          <module name="com.example.currentmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())

    val currentModuleName = "com.example.currentmodule"
    val currentModule = PsiTestUtil.addModule(
      project, PluginModuleType.getInstance(), currentModuleName, myFixture.tempDirFixture.findOrCreateDir(currentModuleName)
    )
    PsiTestUtil.addLibrary(
      currentModule, "${DevkitJavaTestsUtil.TESTDATA_ABSOLUTE_PATH}contentModules/com.example.plugin.with.internal.module.jar"
    )

    val testedFile = myFixture.addXmlFile(
      "com.example.currentmodule/com.example.currentmodule.xml",
      """
      <idea-plugin>
        <dependencies>
          <module name="<error descr="The 'com.example.internalmodule' module is internal and declared in namespace 'another-namespace' in 'com.example.plugin.with.internalmodule/…/plugin.xml', so it cannot be accessed from module 'com.example.currentmodule', which is declared in namespace 'test-namespace' in 'com.example.plugin/…/plugin.xml'">com.example.internalmodule</error>"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test should report module dependency that is from different namespace and provider in a library`() {
    myFixture.addModuleWithPluginDescriptor(
      "com.example.internalmodule",
      "com.example.internalmodule/com.example.internalmodule.xml",
      """
      <idea-plugin visibility="internal">
      </idea-plugin>
      """.trimIndent())

    myFixture.addModuleWithPluginDescriptor(
      "com.example.plugin",
      "com.example.plugin/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin</id>
        <content namespace="test-namespace">
          <module name="com.example.currentmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())

    val currentModuleName = "com.example.currentmodule"
    val currentModule = PsiTestUtil.addModule(
      project, PluginModuleType.getInstance(), currentModuleName, myFixture.tempDirFixture.findOrCreateDir(currentModuleName)
    )
    PsiTestUtil.addLibrary(
      currentModule,
      "${DevkitJavaTestsUtil.TESTDATA_ABSOLUTE_PATH}contentModules/com.example.plugin.with.internal.module-another-namespace.jar"
    )

    val testedFile = myFixture.addXmlFile(
      "com.example.currentmodule/com.example.currentmodule.xml",
      """
      <idea-plugin>
        <dependencies>
          <module name="<error descr="The 'com.example.internalmodule' module is internal and declared in namespace 'another-namespace' in 'plugin.xml', so it cannot be accessed from module 'com.example.currentmodule', which is declared in namespace 'test-namespace' in 'plugin.xml'">com.example.internalmodule</error>"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test should report module dependency that is private in source, but public in library`() {
    myFixture.addModuleWithPluginDescriptor(
      "com.example.plugin.with.public.or.private.module",
      "com.example.plugin.with.public.or.private.module/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.public.or.private.module</id>
        <content>
          <module name="com.example.public.or.private.module"/>
        </content>
      </idea-plugin>
      """.trimIndent())

    // this module is duplicated by the library added with PsiTestUtil.addLibrary below
    myFixture.addModuleWithPluginDescriptor(
      "com.example.public.or.private.module",
      "com.example.public.or.private.module/com.example.public.or.private.module.xml",
      """
      <idea-plugin visibility="private">
      </idea-plugin>
      """.trimIndent())

    myFixture.addModuleWithPluginDescriptor(
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

    val currentModuleName = "com.example.currentmodule"
    val currentModule = PsiTestUtil.addModule(
      project, PluginModuleType.getInstance(), currentModuleName, myFixture.tempDirFixture.findOrCreateDir(currentModuleName)
    )
    PsiTestUtil.addLibrary(currentModule,
                           "${DevkitJavaTestsUtil.TESTDATA_ABSOLUTE_PATH}contentModules/com.example.public.or.private.module.jar")

    val testedFile = myFixture.addXmlFile(
      "com.example.currentmodule/com.example.currentmodule.xml",
      """
      <idea-plugin>
        <dependencies>
          <module name="<error descr="The 'com.example.public.or.private.module' module is private and declared in plugin 'com.example.plugin.with.public.or.private.module', so it cannot be accessed from module 'com.example.currentmodule' declared in plugin 'com.example.plugin.with.currentmodule'">com.example.public.or.private.module</error>"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  fun `test should report module dependency that is private and provided from a library`() {
    val pluginWithPrivateModuleName = "com.example.plugin.with.private.module"
    val pluginWithPrivateModuleModule = PsiTestUtil.addModule(
      myFixture.project, PluginModuleType.getInstance(), pluginWithPrivateModuleName,
      myFixture.tempDirFixture.findOrCreateDir(pluginWithPrivateModuleName)
    )
    myFixture.addXmlFile("com.example.plugin.with.private.module/META-INF/plugin.xml", """
        <idea-plugin>
          <id>com.example.plugin.with.private.module</id>
          <content>
            <module name="com.example.private.module"/>
          </content>
        </idea-plugin>
        """.trimIndent())

    val privateModuleJarPath = "${DevkitJavaTestsUtil.TESTDATA_ABSOLUTE_PATH}contentModules/com.example.private.module.jar"
    PsiTestUtil.addLibrary(pluginWithPrivateModuleModule, privateModuleJarPath)

    myFixture.addModuleWithPluginDescriptor(
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

    val currentModuleName = "com.example.currentmodule"
    val currentModule = PsiTestUtil.addModule(
      project, PluginModuleType.getInstance(), currentModuleName, myFixture.tempDirFixture.findOrCreateDir(currentModuleName)
    )
    PsiTestUtil.addLibrary(currentModule, privateModuleJarPath)

    val testedFile = myFixture.addXmlFile(
      "com.example.currentmodule/com.example.currentmodule.xml",
      """
      <idea-plugin>
        <dependencies>
          <module name="<error descr="The 'com.example.private.module' module is private and declared in plugin 'com.example.plugin.with.private.module', so it cannot be accessed from module 'com.example.currentmodule' declared in plugin 'com.example.plugin.with.currentmodule'">com.example.private.module</error>"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent()
    )
    testHighlighting(testedFile)
  }

  private fun testHighlighting(testedFile: PsiFile) {
    myFixture.testHighlighting(true, true, true, testedFile.virtualFile)
  }
}

class ChangeModuleModuleVisibilityFix : ContentModuleVisibilityInspectionTestBase() {

  fun `test fix change visibility to internal`() {
    myFixture.addModuleWithPluginDescriptor(
      "com.example.plugin.with.privatemodule",
      "com.example.plugin.with.privatemodule/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.privatemodule</id>
        <vendor>ExampleVendor</vendor>
        <content>
          <module name="com.example.privatemodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())
    myFixture.addModuleWithPluginDescriptor(
      "com.example.privatemodule",
      "com.example.privatemodule/com.example.privatemodule.xml",
      // visibility="private" by default
      """
      <idea-plugin>
      </idea-plugin>
      """.trimIndent())

    myFixture.addModuleWithPluginDescriptor(
      "com.example.plugin.with.publicmodule",
      "com.example.plugin.with.publicmodule/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.publicmodule</id>
        <vendor>ExampleVendor</vendor>
        <content>
          <module name="com.example.publicmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())
    val testedFile = myFixture.addModuleWithPluginDescriptor(
      "com.example.publicmodule",
      "com.example.publicmodule/com.example.publicmodule.xml",
      """
      <idea-plugin visibility="public">
        <dependencies>
          <module name="com.example.pri<caret>vatemodule"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent())
    myFixture.configureFromExistingVirtualFile(testedFile.virtualFile)

    val intention = myFixture.findSingleIntention("Make module 'com.example.privatemodule' internal")
    myFixture.launchAction(intention)

    myFixture.checkResult(
      "com.example.privatemodule/com.example.privatemodule.xml",
      //language=XML
      """
      <idea-plugin visibility="internal">
      </idea-plugin>
      """.trimIndent(),
      false
    )
  }

  fun `test fix change visibility to public`() {
    myFixture.addModuleWithPluginDescriptor(
      "com.example.plugin.with.privatemodule",
      "com.example.plugin.with.privatemodule/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.privatemodule</id>
        <vendor>Vendor1</vendor>
        <content>
          <module name="com.example.privatemodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())
    myFixture.addModuleWithPluginDescriptor(
      "com.example.privatemodule",
      "com.example.privatemodule/com.example.privatemodule.xml",
      """
      <idea-plugin visibility="private">
      </idea-plugin>
      """.trimIndent())

    myFixture.addModuleWithPluginDescriptor(
      "com.example.plugin.with.publicmodule",
      "com.example.plugin.with.publicmodule/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.publicmodule</id>
        <vendor>Vendor2</vendor>
        <content>
          <module name="com.example.publicmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())
    val testedFile = myFixture.addModuleWithPluginDescriptor(
      "com.example.publicmodule",
      "com.example.publicmodule/com.example.publicmodule.xml",
      """
      <idea-plugin visibility="public">
        <dependencies>
          <module name="com.example.pri<caret>vatemodule"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent())
    myFixture.configureFromExistingVirtualFile(testedFile.virtualFile)

    val makeInternalIntention = myFixture.filterAvailableIntentions("Make module 'com.example.privatemodule' internal")
    assertEmpty("'Make internal' must not be available if vendors are different", makeInternalIntention)
    val intention = myFixture.findSingleIntention("Make module 'com.example.privatemodule' public")
    myFixture.launchAction(intention)

    myFixture.checkResult(
      "com.example.privatemodule/com.example.privatemodule.xml",
      //language=XML
      """
      <idea-plugin visibility="public">
      </idea-plugin>
      """.trimIndent(),
      false
    )
  }

  fun `test fix set namespace`() {
    myFixture.addModuleWithPluginDescriptor(
      "com.example.plugin.with.internalmodule",
      "com.example.plugin.with.internalmodule/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.internalmodule</id>
        <vendor>ExampleVendor</vendor>
        <content namespace="example_namespace">
          <module name="com.example.internalmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())
    myFixture.addModuleWithPluginDescriptor(
      "com.example.internalmodule",
      "com.example.internalmodule/com.example.internalmodule.xml",
      """
      <idea-plugin visibility="internal">
      </idea-plugin>
      """.trimIndent())

    myFixture.addModuleWithPluginDescriptor(
      "com.example.plugin.with.publicmodule",
      "com.example.plugin.with.publicmodule/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.publicmodule</id>
        <vendor>ExampleVendor</vendor>
        <content>
          <module name="com.example.publicmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())
    val testedFile = myFixture.addModuleWithPluginDescriptor(
      "com.example.publicmodule",
      "com.example.publicmodule/com.example.publicmodule.xml",
      """
      <idea-plugin visibility="public">
        <dependencies>
          <module name="com.example.int<caret>ernalmodule"/>
        </dependencies>
      </idea-plugin>
      """.trimIndent())
    myFixture.configureFromExistingVirtualFile(testedFile.virtualFile)

    val intention = myFixture.findSingleIntention("Set namespace in 'com.example.plugin.with.publicmodule' to 'example_namespace'")
    myFixture.launchAction(intention)

    myFixture.checkResult(
      "com.example.plugin.with.publicmodule/META-INF/plugin.xml",
      //language=XML
      """
      <idea-plugin>
        <id>com.example.plugin.with.publicmodule</id>
        <vendor>ExampleVendor</vendor>
        <content namespace="example_namespace">
          <module name="com.example.publicmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent(),
      false
    )
  }

  fun `test fix set namespace for dependency from plugin descriptor`() {
    myFixture.addModuleWithPluginDescriptor(
      "com.example.plugin.with.internalmodule",
      "com.example.plugin.with.internalmodule/META-INF/plugin.xml",
      """
      <idea-plugin>
        <id>com.example.plugin.with.internalmodule</id>
        <vendor>ExampleVendor</vendor>
        <content namespace="example_namespace">
          <module name="com.example.internalmodule"/>
        </content>
      </idea-plugin>
      """.trimIndent())
    myFixture.addModuleWithPluginDescriptor(
      "com.example.internalmodule",
      "com.example.internalmodule/com.example.internalmodule.xml",
      """
      <idea-plugin visibility="internal">
      </idea-plugin>
      """.trimIndent())

    val testedFile = myFixture.addModuleWithPluginDescriptor(
      "com.example.plugin",
      "com.example.plugin/META-INF/plugin.xml",
      """
      <idea-plugin>
          <id>com.example.plugin</id>
          <vendor>ExampleVendor</vendor>
          <dependencies>
              <module name="com.example.internal<caret>module"/>
          </dependencies>
      </idea-plugin>
      """.trimIndent())
    myFixture.configureFromExistingVirtualFile(testedFile.virtualFile)

    val intention = myFixture.findSingleIntention("Set namespace in 'com.example.plugin' to 'example_namespace'")
    myFixture.launchAction(intention)

    myFixture.checkResult(
      "com.example.plugin/META-INF/plugin.xml",
      //language=XML
      """
      <idea-plugin>
          <id>com.example.plugin</id>
          <vendor>ExampleVendor</vendor>
          <dependencies>
              <module name="com.example.internalmodule"/>
          </dependencies>
          <content namespace="example_namespace"/>
      </idea-plugin>
      """.trimIndent(),
      false
    )
  }
}

private fun CodeInsightTestFixture.addModuleWithPluginDescriptor(
  moduleName: String,
  pluginDescriptorFilePath: String,
  @Language("XML") pluginDescriptorContent: String,
): PsiFile {
  PsiTestUtil.addModule(project, PluginModuleType.getInstance(), moduleName, tempDirFixture.findOrCreateDir(moduleName))
  return addXmlFile(pluginDescriptorFilePath, pluginDescriptorContent)
}

private fun CodeInsightTestFixture.addXmlFile(relativePath: String, @Language("XML") fileText: String): PsiFile {
  return addFileToProject(relativePath, fileText)
}
