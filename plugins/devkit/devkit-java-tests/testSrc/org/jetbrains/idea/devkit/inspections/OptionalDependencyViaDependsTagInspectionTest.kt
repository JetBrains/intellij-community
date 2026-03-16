// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

internal class OptionalDependencyViaDependsTagInspectionTest : JavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(OptionalDependencyViaDependsTagInspection::class.java)
  }

  fun `test optional dependency via depends tag`() {
    val pluginXml = myFixture.addFileToProject("plugin.xml", """
      |<idea-plugin>
      |  <depends>anotherPlugin1</depends>
      |  <depends optional="<warning descr="Optional dependency declared by 'depends' tag should be replaced by an optional content module">true</warning>" config-file="my.xml">anotherPlugin2</depends>
      |</idea-plugin>
    """.trimMargin())
    myFixture.testHighlighting(true, true, true, pluginXml.virtualFile)
  }
}
