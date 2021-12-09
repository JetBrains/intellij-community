// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.task.TaskData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsDataStorage
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.PathPrefixTreeMap
import com.intellij.openapi.project.Project
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.containers.MultiMap
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.settings.GradleSettings

/**
 * @see org.jetbrains.plugins.gradle.service.project.GradleTasksIndices
 */
@ApiStatus.Internal
fun getGradleTasks(project: Project): Map<String, MultiMap<String, GradleTaskData>> {
  return CachedValuesManager.getManager(project).getCachedValue(project) {
    CachedValueProvider.Result.create(getGradleTasksMap(project), ExternalProjectsDataStorage.getInstance(project))
  }
}

/**
 * @return `external module path (path to the directory) -> {gradle module path -> {[tasks of this module]}}`
 */
private fun getGradleTasksMap(project: Project): Map<String, MultiMap<String, GradleTaskData>> {
  return getGradleTaskNodesMap(project).mapValues { (_, moduleTasks) ->
    val transformed = MultiMap.create<String, GradleTaskData>()
    for ((gradleModulePath, moduleTaskNodes) in moduleTasks.entrySet()) {
      transformed.putValues(gradleModulePath, moduleTaskNodes.map {
        GradleTaskData(it, gradleModulePath)
      })
    }
    transformed
  }
}

/**
 * @return `external module path (path to the directory) -> {gradle module path -> {[task nodes of this module]}}`
 */
private fun getGradleTaskNodesMap(project: Project): Map<String, MultiMap<String, DataNode<TaskData>>> {
  val tasks = LinkedHashMap<String, MultiMap<String, DataNode<TaskData>>>()
  for (projectTaskData in findGradleTasks(project)) {
    val projectTasks = MultiMap.createOrderedSet<String, DataNode<TaskData>>()
    val modulePaths = PathPrefixTreeMap<String>(":", removeTrailingSeparator = false)
    for (moduleTaskData in projectTaskData.modulesTaskData) {
      modulePaths[moduleTaskData.gradlePath] = moduleTaskData.externalModulePath
      projectTasks.putValues(moduleTaskData.gradlePath, moduleTaskData.tasks)
    }

    for ((gradlePath, externalModulePath) in modulePaths) {
      val moduleTasks = tasks.computeIfAbsent(externalModulePath) { MultiMap.createOrderedSet() }
      val childrenModulesPaths = modulePaths.getAllDescendantKeys(gradlePath)
      for (childModulePath in childrenModulesPaths) {
        moduleTasks.putValues(childModulePath, projectTasks.get(childModulePath))
      }
    }
  }
  return tasks
}

private data class ProjectTaskData(val externalProjectPath: String, val modulesTaskData: List<ModuleTaskData>)
private data class ModuleTaskData(val externalModulePath: String, val gradlePath: String, val tasks: List<DataNode<TaskData>>)

private fun findGradleTasks(project: Project): List<ProjectTaskData> {
  val projectDataManager = ProjectDataManager.getInstance()
  val projects = MultiMap.createOrderedSet<String, DataNode<ModuleData>>()
  for (settings in GradleSettings.getInstance(project).linkedProjectsSettings) {
    val projectInfo = projectDataManager.getExternalProjectData(project, GradleConstants.SYSTEM_ID, settings.externalProjectPath)
    val compositeParticipants = settings.compositeBuild?.compositeParticipants ?: emptyList()
    val compositeProjects = compositeParticipants.flatMap { it.projects.map { module -> module to it.rootPath } }.toMap()
    val projectNode = projectInfo?.externalProjectStructure ?: continue
    val moduleNodes = ExternalSystemApiUtil.getChildren(projectNode, ProjectKeys.MODULE)
    for (moduleNode in moduleNodes) {
      val externalModulePath = moduleNode.data.linkedExternalProjectPath
      val projectPath = compositeProjects[externalModulePath] ?: settings.externalProjectPath
      projects.putValue(projectPath, moduleNode)
    }
  }
  val projectTasksData = ArrayList<ProjectTaskData>()
  for ((externalProjectPath, modulesNodes) in projects.entrySet()) {
    val modulesTaskData = modulesNodes.map(::getModuleTasks)
    val taskProjectPath = modulesTaskData.firstOrNull()?.tasks?.firstOrNull()?.data?.linkedExternalProjectPath
    projectTasksData.add(ProjectTaskData(taskProjectPath ?: externalProjectPath, modulesTaskData))
  }
  return projectTasksData
}

private fun getModuleTasks(moduleNode: DataNode<ModuleData>): ModuleTaskData {
  val moduleData = moduleNode.data
  val externalModulePath = moduleData.linkedExternalProjectPath
  val gradlePath = GradleProjectResolverUtil.getGradlePath(moduleData)
    .removeSuffix(":")
  val tasks = ExternalSystemApiUtil.getChildren(moduleNode, ProjectKeys.TASK)
    .filter { it.data.name.isNotEmpty() }

  val taskPathPrefix = tasks.firstOrNull()?.data?.name?.substringBeforeLast(':', "").nullize()
  val linkedExternalProjectPath = tasks.firstOrNull()?.data?.linkedExternalProjectPath
  return ModuleTaskData(linkedExternalProjectPath ?: externalModulePath, taskPathPrefix ?: gradlePath, tasks)
}