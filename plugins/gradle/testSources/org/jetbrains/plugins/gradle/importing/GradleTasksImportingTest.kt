// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.externalSystem.model.task.TaskData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import org.junit.Test

class GradleTasksImportingTest : BuildViewMessagesImportingTestCase() {

  @Test
  fun `test basic tasks importing`() {
    createSettingsFile("include 'subproject'")
    importProject()
    assertSyncViewTreeEquals("-\n" +
                             " finished")

    assertThat(findTasks(projectPath).map { it.name })
      .contains("help", "init", "wrapper", "projects", "tasks", "properties")

    assertThat(findTasks(path("subproject")).map { it.name })
      .contains("help", "projects", "tasks", "properties")
  }

  @Test
  @TargetVersions("5.0+")
  fun `test task registration failure doesn't break other tasks importing of unrelated projects`() {
    createSettingsFile("include 'project1', 'project2'")
    createProjectSubFile("project1/build.gradle", """
      tasks.register('badTask') {
        throw new RuntimeException()
      }
    """.trimIndent())
    importProject()
    assertSyncViewTreeEquals("-\n" +
                             " -finished\n" +
                             "  Can not load tasks for project ':project1'")

    assertThat(findTasks(projectPath)).isNotEmpty
    assertThat(findTasks(path("project1"))).isEmpty()
    assertThat(findTasks(path("project2"))).isNotEmpty
  }

  private fun findTasks(projectPath: String) : Collection<TaskData> {
    return ExternalSystemApiUtil.findProjectTasks(myProject, SYSTEM_ID, projectPath)
  }
}