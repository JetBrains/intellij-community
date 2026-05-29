// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language

internal class EmptyPluginXmlTagInspectionTest : JavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(EmptyPluginXmlTagInspection::class.java)
  }

  // extensionPoints

  fun `test empty extensionPoints tag`() {
    doTest("""
      <idea-plugin>
        <warning descr="Empty <extensionPoints> tag"><extensionPoints/></warning>
      </idea-plugin>
    """.trimIndent())
  }

  fun `test non-empty extensionPoints tag`() {
    doTest("""
      <idea-plugin>
        <extensionPoints>
          <extensionPoint name="com.example.ep" interface="com.example.MyEp"/>
        </extensionPoints>
      </idea-plugin>
    """.trimIndent())
  }

  // extensions

  fun `test empty extensions tag`() {
    doTest("""
      <idea-plugin>
        <warning descr="Empty <extensions> tag"><extensions defaultExtensionNs="com.intellij"/></warning>
      </idea-plugin>
    """.trimIndent())
  }

  fun `test empty extensions tag non-self-closing`() {
    doTest("""
      <idea-plugin>
        <warning descr="Empty <extensions> tag"><extensions></extensions></warning>
      </idea-plugin>
    """.trimIndent())
  }

  fun `test empty extensions tag with comment`() {
    doTest("""
      <idea-plugin>
        <warning descr="Empty <extensions> tag"><extensions><!-- TODO: register extensions here --></extensions></warning>
      </idea-plugin>
    """.trimIndent())
  }

  fun `test empty extensions tag with namespace non-self-closing`() {
    doTest("""
      <idea-plugin>
        <warning descr="Empty <extensions> tag"><extensions defaultExtensionNs="com.intellij"></extensions></warning>
      </idea-plugin>
    """.trimIndent())
  }

  fun `test non-empty extensions tag`() {
    doTest("""
      <idea-plugin>
        <extensions defaultExtensionNs="com.intellij">
          <registeredExtension/>
        </extensions>
      </idea-plugin>
    """.trimIndent())
  }

  // applicationListeners

  fun `test empty applicationListeners tag`() {
    doTest("""
      <idea-plugin>
        <warning descr="Empty <applicationListeners> tag"><applicationListeners/></warning>
      </idea-plugin>
    """.trimIndent())
  }

  fun `test non-empty applicationListeners tag`() {
    doTest("""
      <idea-plugin>
        <applicationListeners>
          <listener class="com.example.MyListener" topic="com.example.MyTopic"/>
        </applicationListeners>
      </idea-plugin>
    """.trimIndent())
  }

  // projectListeners

  fun `test empty projectListeners tag`() {
    doTest("""
      <idea-plugin>
        <warning descr="Empty <projectListeners> tag"><projectListeners/></warning>
      </idea-plugin>
    """.trimIndent())
  }

  fun `test non-empty projectListeners tag`() {
    doTest("""
      <idea-plugin>
        <projectListeners>
          <listener class="com.example.MyListener" topic="com.example.MyTopic"/>
        </projectListeners>
      </idea-plugin>
    """.trimIndent())
  }

  // actions

  fun `test empty actions tag`() {
    doTest("""
      <idea-plugin>
        <warning descr="Empty <actions> tag"><actions/></warning>
      </idea-plugin>
    """.trimIndent())
  }

  fun `test non-empty actions tag with action`() {
    doTest("""
      <idea-plugin>
        <actions>
          <action id="com.example.MyAction" class="com.example.MyAction"/>
        </actions>
      </idea-plugin>
    """.trimIndent())
  }

  fun `test non-empty actions tag with group`() {
    doTest("""
      <idea-plugin>
        <actions>
          <group id="com.example.MyGroup"/>
        </actions>
      </idea-plugin>
    """.trimIndent())
  }

  fun `test non-empty actions tag with reference`() {
    doTest("""
      <idea-plugin>
        <actions>
          <reference ref="com.example.MyAction"/>
        </actions>
      </idea-plugin>
    """.trimIndent())
  }

  private fun doTest(@Language("XML") xml: String) {
    val file = addPluginXml(xml)
    myFixture.testHighlighting(true, false, true, file.virtualFile)
  }

  private fun addPluginXml(xml: String): PsiFile {
    return myFixture.addFileToProject("plugin.xml", xml)
  }
}
