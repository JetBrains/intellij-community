// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.util.GradleTaskData

interface GradleTasksIndices {

  /**
   * Finds all tasks which are accessible from Gradle module with module path.
   */
  fun findTasks(modulePath: String): List<GradleTaskData>

  /**
   * Finds all tasks which are accessible from Gradle module with module path.
   * @param matcher is task matcher. eg. task, :task, :module:path:task.
   */
  fun findTasks(modulePath: String, matcher: String): List<GradleTaskData>

  /**
   * Checks that [task] can be determined by [matcher] in Gradle module that defined by [modulePath].
   */
  fun isMatchedTask(task: GradleTaskData, modulePath: String, matcher: String): Boolean

  /**
   * Evaluates all [task] names which can be used from Gradle module that defined by [modulePath].
   * @return empty list if task cannot be runned from defined Gradle module.
   */
  fun getPossibleTaskNames(task: GradleTaskData, modulePath: String): List<String>

  /**
   * Get ordered all tasks that can be executed from Gradle module that defined by [modulePath].
   */
  fun getTasksCompletionVariances(modulePath: String): Map<String, List<GradleTaskData>>

  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<GradleTasksIndices>()
  }
}