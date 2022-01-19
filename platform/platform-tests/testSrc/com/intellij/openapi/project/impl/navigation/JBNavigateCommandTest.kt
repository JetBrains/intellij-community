// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl.navigation

import com.intellij.navigation.areOriginsEqual
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.*
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JBNavigateCommandTest: NavigationTestBase() {
  companion object {
    @JvmField @ClassRule val appRule = ApplicationRule()
  }

  private lateinit var projectName: String

  @Test fun pathWithLineColumn() = runNavigationTest(
    navigationAction = { navigate(mapOf("path" to "A.java:1:5")) }
  ) {
      assertThat(currentElement.containingFile.name).isEqualTo("A.java")
      with(currentLogicalPosition) {
        assertThat(line).isEqualTo(1)
        assertThat(column).isEqualTo(5)
      }
  }

  @Test fun pathWithLine() = runNavigationTest(
    navigationAction = { navigate(mapOf("path" to "A.java:2")) }
  ) {
    assertThat(currentElement.containingFile.name).isEqualTo("A.java")
    assertThat(currentLogicalPosition.line).isEqualTo(2)
  }

  @Test fun path1() = runNavigationTest(
    navigationAction = { navigate(mapOf("path" to "A.java")) }
  ) {
    assertThat(currentElement.name).isEqualTo("A.java")
  }

  @Test fun pathOpenProject() = runBlocking {
    createOrLoadProject(tempDir, useDefaultProjectSettings = false) { project ->
      withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
        setUpProject(project)
      }
    }
    withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
      PlatformTestUtil.forceCloseProjectWithoutSaving(project)

      navigate(mapOf("path" to "A.java"))
      UIUtil.dispatchAllInvocationEvents()

      ProjectManager.getInstance().openProjects.find { it.name == projectName }!!.use { recentProject ->
        project = recentProject
        assertThat(currentElement.name).isEqualTo("A.java")
      }
    }
  }

  @Test fun compareOrigins() {
    val equalOrigins = listOf(
      "https://github.com/JetBrains/intellij.git",
      "https://github.com/JetBrains/intellij",
      "http://github.com/JetBrains/intellij",
      "ssh://git@github.com:JetBrains/intellij.git",
      "ssh://user@github.com:JetBrains/intellij.git",
      "git@github.com:JetBrains/intellij.git",
      "user@github.com:JetBrains/intellij.git",
    )

    equalOrigins.forEach { first ->
      equalOrigins.forEach { second ->
        assertTrue(areOriginsEqual(first, second), "Non equal: '$first' and '$second'")
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
      nonEqualOrigins.forEach { second ->
        assertFalse(areOriginsEqual(first, second), "Equal: '$first' and '$second'")
      }
    }
  }

  override fun setUpProject(project: Project) {
    super.setUpProject(project)
    projectName = project.name
  }

  private fun navigate(parameters: Map<String, String>) {
    val query = parameters.asSequence().fold("project=${projectName}") { acc, e -> acc + "&${e.key}=${e.value}" }
    JBProtocolCommand.execute("idea/navigate/reference?${query}")
  }
}
