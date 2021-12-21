// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.util.ThreeState
import com.intellij.util.containers.nullize
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.model.data.BuildParticipant
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleTaskData
import org.jetbrains.plugins.gradle.util.getGradleTasks

class GradleTasksIndicesImpl(private val project: Project) : GradleTasksIndices {

  private fun getExternalProjectPath(modulePath: String): String? {
    return ExternalSystemUtil.getExternalProjectInfo(project, GradleConstants.SYSTEM_ID, modulePath)
      ?.externalProjectPath
  }

  private fun getGradleModulePath(externalProjectPath: String, modulePath: String): String? {
    val moduleNode = CachedModuleDataFinder.findModuleData(project, modulePath)
    return when {
      moduleNode == null -> null
      externalProjectPath == modulePath -> ""
      else -> moduleNode.data.id.removePrefix(":")
    }
  }

  override fun findTasks(modulePath: String): List<GradleTaskData> {
    val externalProjectPath = getExternalProjectPath(modulePath) ?: return emptyList()
    val projectTasks = getGradleTasks(project)[externalProjectPath] ?: return emptyList()
    return projectTasks.values().toList()
  }

  override fun findTasks(modulePath: String, matcher: String): List<GradleTaskData> {
    val tasks = findTasks(modulePath)
    val tasksMatchStatus = tasks.map { it to isMatchedTask(it, modulePath, matcher) }
    val matchedTasks = tasksMatchStatus.filter { it.second == ThreeState.YES }.map { it.first }
    val partiallyMatchedTasks = tasksMatchStatus.filter { it.second == ThreeState.UNSURE }.map { it.first }
    return matchedTasks.nullize() ?: partiallyMatchedTasks
  }

  override fun isMatchedTask(task: GradleTaskData, modulePath: String, matcher: String): ThreeState {
    val possibleNames = getPossibleTaskNames(task, modulePath)
    return when {
      matcher in possibleNames -> ThreeState.YES
      possibleNames.any { it.startsWith(matcher) } -> ThreeState.UNSURE
      else -> ThreeState.NO
    }
  }

  override fun getPossibleTaskNames(task: GradleTaskData, modulePath: String): Set<String> {
    val taskName = task.name
    val taskPath = getTaskPathUnderModuleProject(task, modulePath)
    if (!isTaskFromModuleProject(task, modulePath)) {
      if (isModuleFromCompositeProject(modulePath) &&
          !isTaskFromCompositeProject(task)) {
        return setOf()
      }
      return setOf(taskPath)
    }
    val relativeTaskPath = getTaskPathUnderModule(task, modulePath)
    if (relativeTaskPath == null) {
      return setOf(taskPath)
    }
    if (task.isInherited) {
      return setOf(taskName)
    }
    return setOf(task.name, taskPath, relativeTaskPath)
  }

  override fun getTasksCompletionVariances(modulePath: String): Map<String, List<GradleTaskData>> {
    return findTasks(modulePath).asSequence()
      .filterNot { it.isInherited }
      .flatMap { task -> getPossibleTaskNames(task, modulePath).map { it to task } }
      .sortedWith(Comparator.comparing({ it.first }, GRADLE_COMPLETION_COMPARATOR))
      .groupBy { it.first }
      .mapValues { it.value.map { (_, task) -> task } }
  }

  private fun getModuleCompositeProject(modulePath: String): BuildParticipant? {
    val settings = GradleSettings.getInstance(project)
    val projectSettings = settings.getLinkedProjectSettings(modulePath)
    val compositeBuild = projectSettings?.compositeBuild
    val buildParticipants = compositeBuild?.compositeParticipants
    return buildParticipants?.find { modulePath in it.projects }
  }

  private fun getTaskCompositeProject(task: GradleTaskData): BuildParticipant? {
    val moduleData = task.node.parent?.data as? ModuleData ?: return null
    return getModuleCompositeProject(moduleData.linkedExternalProjectPath)
  }

  private fun isTaskFromModuleProject(task: GradleTaskData, modulePath: String): Boolean {
    return getTaskCompositeProject(task)?.rootPath == getModuleCompositeProject(modulePath)?.rootPath
  }

  private fun isTaskFromCompositeProject(task: GradleTaskData): Boolean {
    return getTaskCompositeProject(task) != null
  }

  private fun isModuleFromCompositeProject(modulePath: String): Boolean {
    return getModuleCompositeProject(modulePath) != null
  }

  private fun getTaskPathUnderModuleProject(task: GradleTaskData, modulePath: String): String {
    val taskFqnPath = task.getFqnTaskName()
    if (!isTaskFromModuleProject(task, modulePath)) return taskFqnPath
    val buildParticipant = getTaskCompositeProject(task) ?: return taskFqnPath
    val buildParticipantName = buildParticipant.rootProjectName
    return taskFqnPath.removePrefix(":$buildParticipantName")
  }

  private fun getTaskPathUnderModule(task: GradleTaskData, modulePath: String): String? {
    val externalProjectPath = getExternalProjectPath(modulePath) ?: return null
    val gradleModulePath = getGradleModulePath(externalProjectPath, modulePath) ?: return null
    val surroundedModulePath = if (gradleModulePath.isEmpty()) ":" else ":$gradleModulePath:"
    val taskFqnPath = task.getFqnTaskName()
    if (taskFqnPath.startsWith(surroundedModulePath)) {
      return taskFqnPath.removePrefix(surroundedModulePath)
    }
    return null
  }

  companion object {
    val GRADLE_COMPLETION_COMPARATOR = Comparator<String> { o1, o2 ->
      when {
        o1.startsWith("--") && o2.startsWith("--") -> o1.compareTo(o2)
        o1.startsWith("-") && o2.startsWith("--") -> -1
        o1.startsWith("--") && o2.startsWith("-") -> 1
        o1.startsWith(":") && o2.startsWith(":") -> o1.compareTo(o2)
        o1.startsWith(":") && o2.startsWith("-") -> -1
        o1.startsWith("-") && o2.startsWith(":") -> 1
        o2.startsWith("-") -> -1
        o2.startsWith(":") -> -1
        o1.startsWith("-") -> 1
        o1.startsWith(":") -> 1
        else -> o1.compareTo(o2)
      }
    }
  }
}