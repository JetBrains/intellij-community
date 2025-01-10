package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkProvider
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_INTERNAL_JAVA
import com.intellij.openapi.externalSystem.service.internal.AbstractExternalSystemTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.VfsTestUtil
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings

class LocalGradleExecutionAwareTest : LightPlatformTestCase() {

  fun `test Given project using valid JAVA_INTERNAL When prepare JVM execution Then SDK info is returned as resolved`() {
    GradleSettings.getInstance(project).linkedProjectsSettings = listOf(GradleProjectSettings().apply {
      this.externalProjectPath = project.basePath
      this.gradleJvm = USE_INTERNAL_JAVA
    })
    (prepareJvmForExecution() as SdkLookupProvider.SdkInfo.Resolved).run {
      assertEquals(ExternalSystemJdkProvider.getInstance().getInternalJdk().name, name)
      assertEquals(ExternalSystemJdkProvider.getInstance().getInternalJdk().homePath, homePath)
    }
  }

  fun `test Given project using Daemon Jvm criteria When prepare JVM execution Then any validation is skipped`() {
    VfsTestUtil.createFile(project.baseDir, "gradle/gradle-daemon-jvm.properties", "toolchainVersion=17")
    GradleSettings.getInstance(project).linkedProjectsSettings = listOf(GradleProjectSettings().apply {
      this.externalProjectPath = project.basePath
      this.gradleJvm = "Invalid jdk.table entry"
    })
    prepareJvmForExecution().run {
      assertNull(this)
    }
  }

  private fun prepareJvmForExecution() =
    LocalGradleExecutionAware().prepareJvmForExecution(DummyTask(project), project.basePath!!, DummyTaskNotificationListener(), project)

  private class DummyTask(project: Project) : AbstractExternalSystemTask(
    ProjectSystemId.IDE, ExternalSystemTaskType.EXECUTE_TASK, project, ""
  ) {
    override fun doCancel(): Boolean = true

    override fun doExecute() {}
  }

  private class DummyTaskNotificationListener : ExternalSystemTaskNotificationListener {
    override fun onStart(id: ExternalSystemTaskId, workingDir: String?) {}

    override fun onEnd(id: ExternalSystemTaskId) {}
  }
}