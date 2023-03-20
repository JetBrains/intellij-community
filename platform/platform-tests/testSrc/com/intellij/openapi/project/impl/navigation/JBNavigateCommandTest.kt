// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HttpUrlsUsage")

package com.intellij.openapi.project.impl.navigation

import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.navigation.areOriginsEqual
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.createOrLoadProject
import com.intellij.testFramework.useProjectAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.ClassRule
import org.junit.Test

class JBNavigateCommandTest : NavigationTestBase() {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  private lateinit var projectName: String

  @Test
  fun pathWithLineColumn() = runNavigationTest(
    navigationAction = { navigate(mapOf("path" to "A.java:1:5")) }
  ) {
    assertThat(getCurrentElement().containingFile.name).isEqualTo("A.java")
    val position = getCurrentLogicalPosition()
    assertThat(position.line).isEqualTo(1)
    assertThat(position.column).isEqualTo(5)
  }

  @Test
  fun pathWithLine() {
    runNavigationTest(
      navigationAction = { navigate(mapOf("path" to "A.java:2")) }
    ) {
      assertThat(getCurrentElement().containingFile.name).isEqualTo("A.java")
      assertThat(getCurrentLogicalPosition().line).isEqualTo(2)
    }
  }

  @Test
  fun path1() {
    runNavigationTest(
      navigationAction = { navigate(mapOf("path" to "A.java")) }
    ) {
      assertThat(getCurrentElement().name).isEqualTo("A.java")
    }
  }

  @Test
  fun pathOpenProject(): Unit = runBlocking {
    val projectManager = ProjectManagerEx.getInstanceEx()
    val prevProjects = projectManager.openProjects.toHashSet()

    createOrLoadProject(tempDir, useDefaultProjectSettings = false) { project ->
      setUpProject(project)
      RecentProjectsManagerBase.getInstanceEx().projectOpened(project)
    }
    try {
      val result = withContext(Dispatchers.EDT) {
        navigate(mapOf("path" to "A.java"))
      }
      assertThat(result).isNull()
      withContext(Dispatchers.EDT) {
        yield()
      }
      projectManager.openProjects.find { it.name == projectName }!!.useProjectAsync { recentProject ->
        project = recentProject
        readAction {
          assertThat(getCurrentElement().name).isEqualTo("A.java")
        }
      }
    }
    finally {
      projectManager.openProjects.asSequence()
        .filter { it !in prevProjects }
        .forEach { projectManager.forceCloseProjectAsync(it) }
    }
  }

  @Test
  fun compareOrigins() {
    val equalOrigins = listOf(
      "https://github.com/JetBrains/intellij.git",
      "https://github.com/JetBrains/intellij",
      "http://github.com/JetBrains/intellij",
      "ssh://git@github.com:JetBrains/intellij.git",
      "ssh://user@github.com:JetBrains/intellij.git",
      "git@github.com:JetBrains/intellij.git",
      "user@github.com:JetBrains/intellij.git",
    )

    for (first in equalOrigins) {
      for (second in equalOrigins) {
        assertThat(areOriginsEqual(first, second)).describedAs("Non equal: '$first' and '$second'").isTrue
      }
    }

    val nonEqualOrigins = listOf(
      "https://github.bom/JetBrains/intellij.git",
      "https://github.com/JetBrains/intellij.git.git",
      "http://github.com/JetBraind/intellij",
      "http://github.com:8080/JetBrains/intellij",
      "http://github.com",
      "ssh://git@github.com:JetBrains",
      "ssh://user@github.bom:JetBrains/intellij.git",
      "git@github.com:JetBrains/go",
    )
    equalOrigins.forEach { first ->
      for (second in nonEqualOrigins) {
        assertThat(areOriginsEqual(first, second)).describedAs("Equal: '$first' and '$second'").isFalse
      }
    }
  }

  override suspend fun setUpProject(project: Project) {
    super.setUpProject(project)
    projectName = project.name
  }

  private suspend fun navigate(parameters: Map<String, String>): String? {
    val query = parameters.asSequence().fold("project=${projectName}") { acc, e -> acc + "&${e.key}=${e.value}" }
    return JBProtocolCommand.execute("idea/navigate/reference?${query}")
  }
}
