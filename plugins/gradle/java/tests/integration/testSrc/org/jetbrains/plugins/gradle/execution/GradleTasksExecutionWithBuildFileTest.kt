// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runners.Parameterized

class GradleTasksExecutionWithBuildFileTest : GradleTasksExecutionTestCase() {

  @Test
  fun `run task with specified build file test`() {
    createProjectSubFile("build.gradle", """
      task myTask() { doLast { print 'Hi!' } }
      """.trimIndent())
    createProjectSubFile("build007.gradle", """
      task anotherTask() { doLast { print 'Hi, James!' } }
      """.trimIndent())

    assertThat(runTaskAndGetErrorOutput(projectPath, "myTask")).isEmpty()
    assertThat(runTaskAndGetErrorOutput("$projectPath/build.gradle", "myTask")).isEmpty()
    assertThat(runTaskAndGetErrorOutput(projectPath, "anotherTask")).contains("Task 'anotherTask' not found in root project 'project'.")
    assertThat(runTaskAndGetErrorOutput("$projectPath/build007.gradle", "anotherTask")).isEmpty()
    assertThat(runTaskAndGetErrorOutput("$projectPath/build007.gradle", "myTask")).contains(
      "Task 'myTask' not found in root project 'project'.")

    assertThat(runTaskAndGetErrorOutput("$projectPath/build.gradle", "myTask", "-b foo")).contains("The specified build file",
                                                                                                   "foo' does not exist.")
  }

  companion object {
    /**
     * The `-b` (`--build-file`) command line option was removed in Gradle 8.0 and is deprecated since 7.3,
     * so the test is run against the latest Gradle version which still supports it.
     */
    private const val GRADLE_VERSION_WITH_BUILD_FILE_OPTION = "7.0.2"

    @Parameterized.Parameters(name = "with Gradle-{0}")
    @JvmStatic
    fun tests(): Collection<Array<out String>> = arrayListOf(arrayOf(GRADLE_VERSION_WITH_BUILD_FILE_OPTION))
  }
}
