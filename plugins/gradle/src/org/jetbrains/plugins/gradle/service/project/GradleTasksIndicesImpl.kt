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

  override fun findTasks(modulePath: String): List<GradleTaskData> {
    return moduleContext(modulePath).findTasks()
  }

  override fun findTasks(modulePath: String, matcher: String): List<GradleTaskData> {
    return moduleContext(modulePath).findTasks(matcher)
  }

  override fun isMatchedTask(task: GradleTaskData, modulePath: String, matcher: String): ThreeState {
    return taskContext(task, modulePath).isMatchedTask(matcher)
  }

  override fun getPossibleTaskNames(task: GradleTaskData, modulePath: String): Set<String> {
    return taskContext(task, modulePath).getPossibleTaskNames()
  }

  private fun moduleContext(modulePath: String) = ModuleResolutionContext(project, modulePath)

  private fun taskContext(task: GradleTaskData, modulePath: String) = TaskResolutionContext(moduleContext(modulePath), task)

  private fun ModuleResolutionContext.taskContext(task: GradleTaskData) = TaskResolutionContext(this, task)

  private fun ModuleResolutionContext.findTasks(): List<GradleTaskData> {
    val externalProjectPath = externalProjectPath ?: return emptyList()
    val projectTasks = getGradleTasks(project)[externalProjectPath] ?: return emptyList()
    return projectTasks.values().toList()
  }

  private fun ModuleResolutionContext.findTasks(matcher: String): List<GradleTaskData> {
    val tasksMatchStatus = findTasks().map { it to taskContext(it).isMatchedTask(matcher) }
    return tasksMatchStatus.filter { it.second == ThreeState.YES }.map { it.first }.nullize()
           ?: tasksMatchStatus.filter { it.second == ThreeState.UNSURE }.map { it.first }
  }

  private fun TaskResolutionContext.isMatchedTask(matcher: String): ThreeState {
    val possibleNames = getPossibleTaskNames()
    return when {
      matcher in possibleNames -> ThreeState.YES
      possibleNames.any { it.startsWith(matcher) } -> ThreeState.UNSURE
      else -> ThreeState.NO
    }
  }

  private fun TaskResolutionContext.getPossibleTaskNames(): Set<String> {
    val taskName = name
    val taskPath = pathUnderModuleProject
    if (!isFromModuleProject) {
      if (moduleContext.isFromCompositeProject &&
          !isFromCompositeProject) {
        return setOf()
      }
      return setOf(taskPath)
    }
    val relativeTaskPath = pathUnderModule
    if (relativeTaskPath == null) {
      return setOf(taskPath)
    }
    if (isInherited) {
      return setOf(taskName)
    }
    return setOf(taskName, taskPath, relativeTaskPath)
  }

  override fun getTasksCompletionVariances(modulePath: String): Map<String, List<GradleTaskData>> {
    return findTasks(modulePath).asSequence()
      .filterNot { it.isInherited }
      .flatMap { task -> getPossibleTaskNames(task, modulePath).map { it to task } }
      .sortedWith(Comparator.comparing({ it.first }, GRADLE_COMPLETION_COMPARATOR))
      .groupBy { it.first }
      .mapValues { it.value.map { (_, task) -> task } }
  }

  private class ModuleResolutionContext(val project: Project, val path: String) {
    val externalProjectPath by lazy {
      ExternalSystemUtil.getExternalProjectInfo(project, GradleConstants.SYSTEM_ID, path)
        ?.externalProjectPath
    }

    val gradlePath by lazy {
      val moduleNode = CachedModuleDataFinder.findModuleData(project, path)
      when {
        moduleNode == null -> null
        externalProjectPath == null -> null
        externalProjectPath == path -> ""
        else -> moduleNode.data.id.removePrefix(":")
      }
    }

    val compositeProject by lazy { getModuleCompositeProject(project, path) }

    val isFromCompositeProject by lazy { compositeProject != null }
  }

  private class TaskResolutionContext(val moduleContext: ModuleResolutionContext, val task: GradleTaskData) {
    val compositeProject by lazy {
      val moduleData = task.node.parent?.data as? ModuleData ?: return@lazy null
      return@lazy getModuleCompositeProject(moduleContext.project, moduleData.linkedExternalProjectPath)
    }

    val isFromCompositeProject by lazy { compositeProject != null }

    val isFromModuleProject by lazy { compositeProject?.rootPath == moduleContext.compositeProject?.rootPath }

    val isInherited by lazy { task.isInherited }

    val name by lazy { task.name }

    val fqnPath by lazy { task.getFqnTaskName() }

    val pathUnderModuleProject by lazy {
      if (!isFromModuleProject) return@lazy fqnPath
      val compositeProject = compositeProject ?: return@lazy fqnPath
      val compositeProjectName = compositeProject.rootProjectName
      return@lazy fqnPath.removePrefix(":$compositeProjectName")
    }

    val pathUnderModule by lazy {
      val gradleModulePath = moduleContext.gradlePath ?: return@lazy null
      val surroundedModulePath = if (gradleModulePath.isEmpty()) ":" else ":$gradleModulePath:"
      if (fqnPath.startsWith(surroundedModulePath)) {
        return@lazy fqnPath.removePrefix(surroundedModulePath)
      }
      return@lazy null
    }
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

    private fun getModuleCompositeProject(project: Project, modulePath: String): BuildParticipant? {
      val settings = GradleSettings.getInstance(project)
      val projectSettings = settings.getLinkedProjectSettings(modulePath)
      val compositeBuild = projectSettings?.compositeBuild
      val buildParticipants = compositeBuild?.compositeParticipants
      return buildParticipants?.find { modulePath in it.projects }
    }
  }
}