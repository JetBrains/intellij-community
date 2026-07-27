// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.javaCodeInsightFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.utils.coroutines.waitCoroutinesBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.idea.devkit.inspections.extractModule.getExtractToJpsModuleCoroutineScope
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

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

  @Test
  fun `quick fix to extract optional dependency`(): Unit = timeoutRunBlocking(20.seconds) {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(projectFixture.get(), true)

    withContext(Dispatchers.EDT) {
      val pluginXml = fixture.addFileToProject("plugin.xml", """
      |<idea-plugin>
      |    <id>plugin</id>
      |    <depends optional="true<caret>" config-file="optional.xml">anotherPlugin</depends>
      |</idea-plugin>
    """.trimMargin())
      fixture.addFileToProject("optional.xml", """
      |<idea-plugin>
      |    <actions>
      |        <action class="com.example.MyAction"/>
      |    </actions>
      |</idea-plugin>
    """.trimMargin())
      fixture.addClass("package com.example; class MyAction {}")
      fixture.configureFromExistingVirtualFile(pluginXml.virtualFile)
    }
    val intention = fixture.findSingleIntention("Extract 'optional.xml' to a content module")
    fixture.launchAction(intention)
    waitCoroutinesBlocking(getExtractToJpsModuleCoroutineScope(projectFixture.get()), timeoutMs = 3_000)

    val moduleName = moduleFixture.get().name
    fixture.checkResult("""
      |<idea-plugin>
      |    <id>plugin</id>
      |    <content>
      |        <module name="$moduleName.optional"/>
      |    </content>
      |</idea-plugin>
    """.trimMargin())
    fixture.checkResult("optional/resources/$moduleName.optional.xml", """
      |<idea-plugin>
      |    <dependencies>
      |        <plugin id="anotherPlugin"/>
      |    </dependencies>
      |    <actions>
      |        <action class="com.example.MyAction"/>
      |    </actions>
      |</idea-plugin>
    """.trimMargin(), true)
  }

  @Test
  fun `quick fix to convert optional dependency to content module`(): Unit = timeoutRunBlocking(timeout = 20.seconds) {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(projectFixture.get(), true)

    withContext(Dispatchers.EDT) {
      val pluginXml = fixture.addFileToProject("plugin.xml", """
      |<idea-plugin>
      |    <id>plugin</id>
      |    <depends optional="true<caret>" config-file="optional.xml">anotherPlugin</depends>
      |</idea-plugin>
    """.trimMargin())
      PsiTestUtil.addModule(
        fixture.project,
        JavaModuleType.getModuleType(),
        "optional",
        fixture.tempDirFixture.findOrCreateDir("optional")
      )

      fixture.addFileToProject("optional.xml", """
      |<idea-plugin>
      |    <actions>
      |        <action class="com.example.MyAction"/>
      |    </actions>
      |</idea-plugin>
    """.trimMargin())
      fixture.addFileToProject("optional/com/example/MyAction.java", "package com.example; class MyAction {}")
      fixture.configureFromExistingVirtualFile(pluginXml.virtualFile)
    }
    val intention = fixture.findSingleIntention("Convert 'optional.xml' to a content module 'optional'")
    fixture.launchAction(intention)
    waitCoroutinesBlocking(getExtractToJpsModuleCoroutineScope(projectFixture.get()), timeoutMs = 3_000)

    fixture.checkResult("""
      |<idea-plugin>
      |    <id>plugin</id>
      |    <content>
      |        <module name="optional"/>
      |    </content>
      |</idea-plugin>
    """.trimMargin())
    fixture.checkResult("optional/resources/optional.xml", """
      |<idea-plugin>
      |    <dependencies>
      |        <plugin id="anotherPlugin"/>
      |    </dependencies>
      |    <actions>
      |        <action class="com.example.MyAction"/>
      |    </actions>
      |</idea-plugin>
    """.trimMargin(), true)
  }

}
