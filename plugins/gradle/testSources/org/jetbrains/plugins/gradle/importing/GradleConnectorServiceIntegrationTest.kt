// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.EXECUTE_TASK
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.assertj.core.api.Assertions.assertThat
import org.gradle.tooling.ProjectConnection
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.junit.Test
import org.junit.runners.Parameterized

class GradleConnectorServiceIntegrationTest : GradleImportingTestCase() {

  @Test
  fun `test connection reuse`() {
    createSettingsFile(including("child"))
    importProject("")

    val projectConnection = requestConnection(projectPath, getExecutionSettings(projectPath))
    assertThrows(IllegalStateException::class.java, "This connection should not be closed explicitly.") {
      projectConnection.close()
    }

    val childProjectPath = path("child")
    assertThat(projectConnection)
      .isEqualTo(requestConnection(projectPath, getExecutionSettings(projectPath)))
      .isEqualTo(requestConnection(projectPath, getExecutionSettings(childProjectPath)))

    val childProjectConnection = requestConnection(childProjectPath, getExecutionSettings(projectPath))
    assertThat(childProjectConnection)
      .isNotEqualTo(projectConnection)
      .isEqualTo(requestConnection(childProjectPath, getExecutionSettings(projectPath)))
      .isEqualTo(requestConnection(childProjectPath, getExecutionSettings(childProjectPath)))
  }

  private fun getExecutionSettings(projectPath: String): GradleExecutionSettings =
    ExternalSystemApiUtil.getExecutionSettings(myProject, projectPath, externalSystemId)

  private fun requestConnection(projectPath: String, executionSettings: GradleExecutionSettings): ProjectConnection {
    val taskId = ExternalSystemTaskId.create(externalSystemId, EXECUTE_TASK, myProject)
    return GradleExecutionHelper.execute(projectPath, executionSettings, taskId, null, null) { it }
  }

  companion object {
    /** It's sufficient to run the test against single gradle version. */
    @Parameterized.Parameters(name = "with Gradle-{0}")
    @JvmStatic
    fun tests(): Collection<Array<out String>> = arrayListOf(arrayOf(BASE_GRADLE_VERSION))
  }
}