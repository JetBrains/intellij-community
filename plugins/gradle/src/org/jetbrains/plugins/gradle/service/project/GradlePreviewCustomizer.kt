// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ContentRootData
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.gradleIdentityPath
import java.io.File

@ApiStatus.OverrideOnly
@ApiStatus.Experimental
interface GradlePreviewCustomizer {
  companion object {
    var EP_NAME: ExtensionPointName<GradlePreviewCustomizer> = create("org.jetbrains.plugins.gradle.previewCustomizer")

    fun getCustomizer(projectPath: String, taskId: ExternalSystemTaskId): GradlePreviewCustomizer =
      EP_NAME.extensionList.firstOrNull { it.isApplicable(projectPath, taskId) } ?: DefaultGradlePreviewCustomizer
  }

  fun isApplicable(projectPath: String, taskId: ExternalSystemTaskId): Boolean

  fun resolvePreviewProjectInfo(projectPath: String, taskId: ExternalSystemTaskId, settings: GradleExecutionSettings?): DataNode<ProjectData>
}

object DefaultGradlePreviewCustomizer : GradlePreviewCustomizer {
  override fun isApplicable(projectPath: String, taskId: ExternalSystemTaskId): Boolean = true

  override fun resolvePreviewProjectInfo(projectPath: String, taskId: ExternalSystemTaskId, settings: GradleExecutionSettings?): DataNode<ProjectData> {
    val projectName: String = File(projectPath).name

    val ideProjectPath = settings?.ideProjectPath
    val mainModuleFileDirectoryPath = ideProjectPath ?: projectPath

    val projectData = ProjectData(GradleConstants.SYSTEM_ID, projectName, projectPath, projectPath)
    val projectDataNode = DataNode(ProjectKeys.PROJECT, projectData, null)

    val moduleData = ModuleData(
      projectName,
      GradleConstants.SYSTEM_ID,
      GradleProjectResolverUtil.getDefaultModuleTypeId(),
      projectName,
      mainModuleFileDirectoryPath,
      projectPath
    )

    moduleData.gradleIdentityPath = ":"

    projectDataNode
      .createChild(ProjectKeys.MODULE, moduleData)
      .createChild(ProjectKeys.CONTENT_ROOT, ContentRootData(GradleConstants.SYSTEM_ID, projectPath))

    return projectDataNode
  }
}