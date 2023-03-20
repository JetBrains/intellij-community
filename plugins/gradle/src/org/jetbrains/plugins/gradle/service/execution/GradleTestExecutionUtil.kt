// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleTestExecutionUtil")

package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.io.toNioPath
import org.jetbrains.plugins.gradle.service.project.GradleTasksIndices
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleTaskData
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLine
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineTask
import kotlin.io.path.isDirectory


fun parseCommandLine(tasksAndArguments: List<String>, arguments: String): GradleCommandLine {
  val sortedArguments = GradleCommandLine.parse(arguments).tokens
  return GradleCommandLine.parse(tasksAndArguments + sortedArguments)
}

fun parseCommandLine(tasksAndArguments: List<String>, arguments: List<String>): GradleCommandLine {
  val sortedArguments = GradleCommandLine.parse(arguments).tokens
  return GradleCommandLine.parse(tasksAndArguments + sortedArguments)
}

fun GradleCommandLine.hasTestTasks(
  project: Project,
  externalProjectPath: String,
): Boolean {
  return tasks.any { isTestTask(project, externalProjectPath, it) }
}

fun GradleCommandLineTask.getTestPatterns(): Set<String> {
  return options
    .filter { GradleConstants.TESTS_ARG_NAME == it.name }
    .flatMap { it.values }
    .toSet()
}

fun isTestTask(
  project: Project,
  externalProjectPath: String,
  task: GradleCommandLineTask
): Boolean {
  if (task.getTestPatterns().isNotEmpty()) {
    return true
  }
  val modulePath = getModulePath(externalProjectPath)
  val tasksIndices = GradleTasksIndices.getInstance(project)
  val tasks = tasksIndices.findTasks(modulePath, task.name)
  return tasks.any { isTestTask(it) }
}

private fun isTestTask(task: GradleTaskData): Boolean {
  return task.isTest || ("check" == task.name && "verification" == task.group)
}

private fun getModulePath(externalProjectPath: String): String {
  val path = externalProjectPath.toNioPath()
  if (path.isDirectory()) {
    return externalProjectPath
  }
  return path.parent.toCanonicalPath()
}