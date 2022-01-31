// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.util.ThreeState
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.model.data.BuildParticipant
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleTaskData
import org.jetbrains.plugins.gradle.util.getGradleTasks
import kotlin.Comparator


class GradleTasksIndicesImpl(private val project: Project) : GradleTasksIndices {

  private fun getModuleContext(modulePath: String) =
    ModuleResolutionContext(project, modulePath)

  private fun getTaskContext(modulePath: String, task: GradleTaskData) =
    TaskResolutionContext(getModuleContext(modulePath), task)

  override fun findTasks(modulePath: String): List<GradleTaskData> {
    return getModuleContext(modulePath).tasks.map { it.task }
  }

  override fun findTasks(modulePath: String, matcher: String): List<GradleTaskData> {
    return getModuleContext(modulePath).findTasks(matcher)
  }

  override fun findTasks(modulePath: String, matchers: List<String>): List<GradleTaskData> {
    val moduleContext = getModuleContext(modulePath)
    return matchers.flatMap { matcher -> moduleContext.findTasks(matcher) }
  }

  override fun isMatchedTask(modulePath: String, task: GradleTaskData, matcher: String): ThreeState {
    return getTaskContext(modulePath, task).isMatchedTask(matcher)
  }

  override fun getPossibleTaskNames(modulePath: String, task: GradleTaskData): Set<String> {
    return getTaskContext(modulePath, task).possibleNames
  }

  override fun getTasksCompletionVariances(modulePath: String): Map<String, List<GradleTaskData>> {
    return findTasks(modulePath).asSequence()
      .filterNot { it.isInherited }
      .flatMap { task -> getPossibleTaskNames(modulePath, task).map { it to task } }
      .sortedWith(Comparator.comparing({ it.first }, GRADLE_COMPLETION_COMPARATOR))
      .groupBy { it.first }
      .mapValues { it.value.map { (_, task) -> task } }
  }

  private class ModuleResolutionContext(val project: Project, val path: String) {
    val externalProjectPath by lazy(::calculateExternalProjectPath)

    val gradlePath by lazy(::calculateGradlePath)

    val compositeProject by lazy { getModuleCompositeProject(project, path) }

    val isFromCompositeProject by lazy { compositeProject != null }

    val tasks by lazy(::calculateTasks)

    val tasksIndex by lazy(::calculateTasksIndex)

    fun findTasks(matcher: String): List<GradleTaskData> {
      return tasksIndex[matcher]
               ?.map { it.task }
             ?: tasks.asSequence()
               .filter { it.isMatchedTask(matcher) == ThreeState.UNSURE }
               .map { it.task }
               .toList()
    }

    private fun calculateExternalProjectPath(): String? {
      return ExternalSystemUtil.getExternalProjectInfo(project, GradleConstants.SYSTEM_ID, path)
        ?.externalProjectPath
    }

    private fun calculateGradlePath(): String? {
      val moduleNode = CachedModuleDataFinder.findModuleData(project, path)
      return when {
        moduleNode == null -> null
        externalProjectPath == null -> null
        externalProjectPath == path -> ""
        else -> moduleNode.data.id.removePrefix(":")
      }
    }

    private fun calculateTasks(): List<TaskResolutionContext> {
      val externalProjectPath = externalProjectPath ?: return emptyList()
      val projectTasks = getGradleTasks(project)[externalProjectPath] ?: return emptyList()
      return projectTasks.values().map { TaskResolutionContext(this, it) }
    }

    private fun calculateTasksIndex(): Map<String, List<TaskResolutionContext>> {
      return tasks.asSequence()
        .flatMap { task -> task.possibleNames.map { it to task } }
        .groupBy({ it.first }, { it.second })
    }
  }

  private class TaskResolutionContext(val moduleContext: ModuleResolutionContext, val task: GradleTaskData) {
    val compositeProject by lazy(::calculateCompositeProject)

    val isFromCompositeProject by lazy { compositeProject != null }

    val isFromModuleProject by lazy { compositeProject?.rootPath == moduleContext.compositeProject?.rootPath }

    val isInherited by lazy { task.isInherited }

    val name by lazy { task.name }

    val fqnPath by lazy { task.getFqnTaskName() }

    val pathUnderModuleProject by lazy(::calculatePathUnderModuleProject)

    val pathUnderModule by lazy(::calculatePathUnderModule)

    val possibleNames by lazy(::calculatePossibleNames)

    fun isMatchedTask(matcher: String): ThreeState {
      return when {
        matcher in possibleNames -> ThreeState.YES
        possibleNames.any { it.startsWith(matcher) } -> ThreeState.UNSURE
        else -> ThreeState.NO
      }
    }

    private fun calculateCompositeProject(): BuildParticipant? {
      val moduleData = task.node.parent?.data as? ModuleData ?: return null
      return getModuleCompositeProject(moduleContext.project, moduleData.linkedExternalProjectPath)
    }

    private fun calculatePathUnderModuleProject(): String {
      if (!isFromModuleProject) return fqnPath
      val compositeProject = compositeProject ?: return fqnPath
      val compositeProjectName = compositeProject.rootProjectName
      return fqnPath.removePrefix(":$compositeProjectName")
    }

    private fun calculatePathUnderModule(): String? {
      val gradleModulePath = moduleContext.gradlePath ?: return null
      val surroundedModulePath = if (gradleModulePath.isEmpty()) ":" else ":$gradleModulePath:"
      if (fqnPath.startsWith(surroundedModulePath)) {
        return fqnPath.removePrefix(surroundedModulePath)
      }
      return null
    }

    private fun calculatePossibleNames(): Set<String> {
      val name = name
      val path = pathUnderModuleProject
      if (!isFromModuleProject) {
        if (moduleContext.isFromCompositeProject &&
            !isFromCompositeProject) {
          return setOf()
        }
        return setOf(path)
      }
      val relativePath = pathUnderModule
      if (relativePath == null) {
        return setOf(path)
      }
      if (isInherited) {
        return setOf(name)
      }
      return setOf(name, path, relativePath)
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