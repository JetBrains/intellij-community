// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.ide.plugins.DependencyCollector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleDependencyCollector : DependencyCollector {
  override fun collectDependencies(project: Project): List<String> {
    val projectInfoList = ProjectDataManager.getInstance().getExternalProjectsData(project, GradleConstants.SYSTEM_ID)
    val result = mutableListOf<String>()
    for (externalProjectInfo in projectInfoList) {
      val projectStructure = externalProjectInfo.externalProjectStructure ?: continue
      val libraries = ExternalSystemApiUtil.findAll(projectStructure, ProjectKeys.LIBRARY)
      for (libraryNode in libraries) {
        val groupId = libraryNode.data.groupId
        val artifactId = libraryNode.data.artifactId
        if (groupId != null && artifactId != null) {
          result.add("$groupId:$artifactId")
        }
      }
    }
    return result
  }
}

class GradleDependencyUpdater : ExternalSystemTaskNotificationListenerAdapter() {
  override fun onEnd(id: ExternalSystemTaskId) {
    if (id.projectSystemId == GradleConstants.SYSTEM_ID && id.type == ExternalSystemTaskType.RESOLVE_PROJECT) {
      id.findProject()?.let {
        ApplicationManager.getApplication().executeOnPooledThread {
          PluginAdvertiserService.getInstance().rescanDependencies(it)
        }
      }
    }
  }
}
