// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.project.ExternalSystemModuleDataIndex
import com.intellij.openapi.externalSystem.service.project.ExternalSystemModuleDataIndex.getDataStorageCachedValue
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.util.ThreeState
import org.jetbrains.plugins.gradle.model.data.BuildParticipant
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleTaskData
import org.jetbrains.plugins.gradle.util.getGradleTasks
import java.util.concurrent.atomic.AtomicReference


class GradleTasksIndicesImpl(private val project: Project) : GradleTasksIndices {

  private fun getModuleContext(modulePath: String): ModuleResolutionContext {
    ProgressManager.checkCanceled()
    val reference = getDataStorageCachedValue(project, project, MODULE_CONTEXT_KEY) {
      AtomicReference<ModuleResolutionContext>()
    }
    return reference.updateAndGet {
      if (it != null && it.path == modulePath) {
        return@updateAndGet it
      }
      ModuleResolutionContext(project, modulePath)
    }
  }

  override fun findTasks(modulePath: String): List<GradleTaskData> {
    return getModuleContext(modulePath).findTasks()
  }

  override fun findTasks(modulePath: String, matcher: String): List<GradleTaskData> {
    return getModuleContext(modulePath).findTasks(matcher)
  }

  override fun findTasks(modulePath: String, matchers: List<String>): List<GradleTaskData> {
    return getModuleContext(modulePath).findTasks(matchers)
  }

  override fun isMatchedTask(modulePath: String, task: GradleTaskData, matcher: String): ThreeState {
    return getModuleContext(modulePath).getTaskContext(task).isMatchedTask(matcher)
  }

  override fun getPossibleTaskNames(modulePath: String, task: GradleTaskData): Set<String> {
    return getModuleContext(modulePath).getTaskContext(task).possibleNames
  }

  override fun getTasksCompletionVariances(modulePath: String): Map<String, List<GradleTaskData>> {
    return getModuleContext(modulePath).tasksCompletionVariances
  }

  private class ModuleResolutionContext(
    val project: Project,
    val path: String
  ) {

    val externalProjectPath by lazy(::calculateExternalProjectPath)

    val gradlePath by lazy(::calculateGradlePath)

    val compositeProject by lazy { findModuleCompositeProject(project, path) }

    val isFromCompositeProject by lazy { compositeProject != null }

    val tasks by lazy(::calculateTasks)

    val tasksIndexByName by lazy(::calculateTasksIndexByName)

    val tasksIndexByData by lazy(::calculateTasksIndexByData)

    val tasksCompletionVariances by lazy(::calculateTasksCompletionVariances)

    fun getTaskContext(task: GradleTaskData): TaskResolutionContext {
      ProgressManager.checkCanceled()
      return tasksIndexByData[task] ?: TaskResolutionContext(this, task)
    }

    fun findTasks(): List<GradleTaskData> {
      ProgressManager.checkCanceled()
      return tasks.map { it.task }
    }

    fun findTasks(matcher: String): List<GradleTaskData> {
      ProgressManager.checkCanceled()
      return tasksIndexByName[matcher]
               ?.map { it.task }
             ?: tasks.asSequence()
               .filter { it.isMatchedTask(matcher) == ThreeState.UNSURE }
               .map { it.task }
               .toList()
    }

    fun findTasks(matchers: List<String>): List<GradleTaskData> {
      ProgressManager.checkCanceled()
      return matchers.flatMap(::findTasks)
    }

    private fun calculateTasksCompletionVariances(): Map<String, List<GradleTaskData>> {
      ProgressManager.checkCanceled()
      return tasks.asSequence()
        .filterNot { it.task.isInherited }
        .flatMap { taskContext -> taskContext.possibleNames.map { it to taskContext.task } }
        .groupBy { it.first }
        .mapValues { it.value.map { (_, task) -> task } }
    }

    private fun calculateExternalProjectPath(): String? {
      ProgressManager.checkCanceled()
      return ExternalSystemUtil.getExternalProjectInfo(project, GradleConstants.SYSTEM_ID, path)
        ?.externalProjectPath
    }

    private fun calculateGradlePath(): String? {
      ProgressManager.checkCanceled()
      val moduleNode = ExternalSystemModuleDataIndex.findModuleNode(project, path)
      return when {
        moduleNode == null -> null
        externalProjectPath == null -> null
        externalProjectPath == path -> ""
        else -> moduleNode.data.id.removePrefix(":")
      }
    }

    private fun calculateTasks(): List<TaskResolutionContext> {
      ProgressManager.checkCanceled()
      val externalProjectPath = externalProjectPath ?: return emptyList()
      val projectTasks = getGradleTasks(project)[externalProjectPath] ?: return emptyList()
      return projectTasks.values().map { TaskResolutionContext(this, it) }
    }

    private fun calculateTasksIndexByName(): Map<String, List<TaskResolutionContext>> {
      ProgressManager.checkCanceled()
      return tasks.asSequence()
        .flatMap { task -> task.possibleNames.map { it to task } }
        .groupBy({ it.first }, { it.second })
    }

    private fun calculateTasksIndexByData(): Map<GradleTaskData, TaskResolutionContext> {
      ProgressManager.checkCanceled()
      return tasks.associateBy { it.task }
    }
  }

  private class TaskResolutionContext(
    val moduleContext: ModuleResolutionContext,
    val task: GradleTaskData
  ) {

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
      return findModuleCompositeProject(moduleContext.project, moduleData.linkedExternalProjectPath)
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

    private val MODULE_CONTEXT_KEY = Key.create<CachedValue<AtomicReference<ModuleResolutionContext>>>("GradleTasksIndicesImpl")

    private fun findModuleCompositeProject(project: Project, modulePath: String): BuildParticipant? {
      val settings = GradleSettings.getInstance(project)
      val projectSettings = settings.getLinkedProjectSettings(modulePath)
      val compositeBuild = projectSettings?.compositeBuild
      val buildParticipants = compositeBuild?.compositeParticipants
      return buildParticipants?.find { modulePath in it.projects }
    }
  }
}