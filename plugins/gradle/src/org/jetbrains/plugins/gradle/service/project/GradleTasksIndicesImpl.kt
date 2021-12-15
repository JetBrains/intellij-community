// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
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
    val gradleModulePath = getGradleModulePath(externalProjectPath, modulePath) ?: return emptyList()
    val projectTasks = getGradleTasks(project)[externalProjectPath] ?: return emptyList()
    return projectTasks.entrySet()
      .filter { it.key.removePrefix(":").startsWith(gradleModulePath) }
      .flatMap { it.value }
  }

  override fun findTasks(modulePath: String, matcher: String): List<GradleTaskData> {
    return findTasks(modulePath)
      .filter { matcher in getPossibleTaskNames(it, modulePath) }
  }

  override fun isMatchedTask(task: GradleTaskData, modulePath: String, matcher: String): Boolean {
    return matcher in getPossibleTaskNames(task, modulePath)
  }

  override fun getPossibleTaskNames(task: GradleTaskData, modulePath: String): List<String> {
    val externalProjectPath = getExternalProjectPath(modulePath) ?: return emptyList()
    val gradleModulePath = getGradleModulePath(externalProjectPath, modulePath) ?: return emptyList()
    val relativeTaskName = task.getFqnTaskName()
      .removePrefix(":")
      .removePrefix(gradleModulePath)
      .removePrefix(":")
    return ArrayList<String>().apply {
      if (isFromCurrentProject(task, modulePath)) {
        addAll(getGradlePathDescendants(relativeTaskName))
      }
      if (!task.isInherited) {
        add(":$relativeTaskName")
      }
    }
  }

  override fun getTasksCompletionVariances(modulePath: String): Map<String, List<GradleTaskData>> {
    return findTasks(modulePath).asSequence()
      .filterNot { it.isInherited }
      .flatMap { task -> getPossibleTaskNames(task, modulePath).map { it to task } }
      .sortedWith(Comparator.comparing({ it.first }, GRADLE_COMPLETION_COMPARATOR))
      .groupBy { it.first }
      .mapValues { it.value.map { (_, task) -> task } }
  }

  private fun getGradlePathDescendants(path: String) = sequence {
    var prefix = ""
    do {
      val current = path.removePrefix(prefix)
      yield(current)
      prefix += current.substringBefore(":") + ":"
    }
    while (':' in current)
  }

  private fun getProjectPath(modulePath: String): String? {
    val settings = GradleSettings.getInstance(project)
    val projectSettings = settings.getLinkedProjectSettings(modulePath)
    val compositeBuild = projectSettings?.compositeBuild
    val buildParticipants = compositeBuild?.compositeParticipants
    val buildParticipant = buildParticipants?.find { modulePath in it.projects }
    return buildParticipant?.rootPath ?: projectSettings?.externalProjectPath
  }

  private fun isFromCurrentProject(task: GradleTaskData, modulePath: String): Boolean {
    val moduleData = task.node.parent?.data as? ModuleData ?: return false
    val taskProjectPath = getProjectPath(moduleData.linkedExternalProjectPath) ?: return false
    val contextProjectPath = getProjectPath(modulePath) ?: return false
    return taskProjectPath == contextProjectPath
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