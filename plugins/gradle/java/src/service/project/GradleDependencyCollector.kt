// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.ide.plugins.DependencyCollector
import com.intellij.openapi.application.readAction
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.util.GradleConstants

internal class GradleDependencyCollector : DependencyCollector {

  override suspend fun collectDependencies(project: Project): Collection<String> {
    val projectStructures = readAction {
      ProjectDataManager.getInstance()
        .getExternalProjectsData(project, GradleConstants.SYSTEM_ID)
        .mapNotNull { it.externalProjectStructure }
    }

    val allDependencies = HashSet<Pair<String, String>>()
    for (node in projectStructures) {
      readAction {
        node.getChildrenSequence(ProjectKeys.MODULE)
          .flatMap { it.getChildrenSequence(GradleSourceSetData.KEY) }
          .flatMap { it.getChildrenSequence(ProjectKeys.LIBRARY_DEPENDENCY) }
          .forEach { libraryData ->
            val target = libraryData.data.target
            val groupId = target.groupId
            val artifactId = target.artifactId
            if (groupId != null && artifactId != null) {
              allDependencies.add(Pair(groupId, artifactId))
            }
          }
      }
    }

    return allDependencies.map { (g, a) -> "$g:$a" }
  }

  private fun <T> DataNode<*>.getChildrenSequence(key: Key<T>) = ExternalSystemApiUtil.getChildren(this, key).asSequence()
}

internal class GradleDependencyUpdater : ExternalSystemTaskNotificationListener {

  override fun onEnd(proojecPath: String, id: ExternalSystemTaskId) {
    if (id.projectSystemId == GradleConstants.SYSTEM_ID
        && id.type == ExternalSystemTaskType.RESOLVE_PROJECT) {
      id.findProject()?.let {
        PluginAdvertiserService.getInstance(it).rescanDependencies()
      }
    }
  }
}
