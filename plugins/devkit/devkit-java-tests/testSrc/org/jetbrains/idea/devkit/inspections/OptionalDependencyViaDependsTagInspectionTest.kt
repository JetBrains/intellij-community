// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.javaCodeInsightFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
internal class OptionalDependencyViaDependsTagInspectionTest {
  private val tempPathFixture = tempPathFixture()
  private val projectFixture = projectFixture(tempPathFixture, openAfterCreation = true)
  @Suppress("unused")
  private val moduleFixture = projectFixture.moduleFixture(addPathToSourceRoot = true, pathFixture = tempPathFixture)
  private val fixture by javaCodeInsightFixture(projectFixture, tempPathFixture)

  @BeforeEach
  fun setUp() {
    fixture.enableInspections(OptionalDependencyViaDependsTagInspection::class.java)
  }

  @Test
  fun `highlight optional dependency via depends tag`(): Unit = timeoutRunBlocking {
    val pluginXml = fixture.addFileToProject("plugin.xml", """
      |<idea-plugin>
      |  <depends>anotherPlugin1</depends>
      |  <depends optional="<warning descr="Optional dependency declared by 'depends' tag should be replaced by an optional content module">true</warning>" config-file="my.xml">anotherPlugin2</depends>
      |</idea-plugin>
    """.trimMargin())
    withContext(Dispatchers.EDT) {
      fixture.testHighlighting(true, true, true, pluginXml.virtualFile)
    }
  }
}
