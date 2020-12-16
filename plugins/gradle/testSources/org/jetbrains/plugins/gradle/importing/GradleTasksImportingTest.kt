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


    val tasksFinder: (projectPath: String) -> Collection<TaskData> = { ExternalSystemApiUtil.findProjectTasks(myProject, SYSTEM_ID, it) }
    assertThat(tasksFinder(projectPath)).isNotEmpty
    assertThat(tasksFinder(path("project1"))).isEmpty()
    assertThat(tasksFinder(path("project2"))).isNotEmpty
  }
}