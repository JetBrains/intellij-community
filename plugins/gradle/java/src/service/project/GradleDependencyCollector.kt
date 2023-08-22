// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.ide.plugins.DependencyCollector
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.util.GradleConstants

internal class GradleDependencyCollector : DependencyCollector {

  override fun collectDependencies(project: Project): Set<String> {
    return ProjectDataManager.getInstance()
      .getExternalProjectsData(project, GradleConstants.SYSTEM_ID)
      .asSequence()
      .mapNotNull { it.externalProjectStructure }
      .flatMap { projectStructure ->
        projectStructure.getChildrenSequence(ProjectKeys.MODULE)
          .flatMap { it.getChildrenSequence(GradleSourceSetData.KEY) }
          .flatMap { it.getChildrenSequence(ProjectKeys.LIBRARY_DEPENDENCY) }
      }.map { it.data.target }
      .mapNotNull { libraryData ->
        val groupId = libraryData.groupId
        val artifactId = libraryData.artifactId
        if (groupId != null && artifactId != null) "$groupId:$artifactId" else null
      }.toSet()
  }

  private fun <T> DataNode<*>.getChildrenSequence(key: Key<T>) = ExternalSystemApiUtil.getChildren(this, key).asSequence()
}

internal class GradleDependencyUpdater : ExternalSystemTaskNotificationListenerAdapter() {

  override fun onEnd(id: ExternalSystemTaskId) {
    if (id.projectSystemId == GradleConstants.SYSTEM_ID
        && id.type == ExternalSystemTaskType.RESOLVE_PROJECT) {
      id.findProject()?.let {
        PluginAdvertiserService.getInstance(it).rescanDependencies()
      }
    }
  }
}
