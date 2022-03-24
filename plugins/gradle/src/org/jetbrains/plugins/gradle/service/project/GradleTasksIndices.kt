// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.ThreeState
import org.jetbrains.plugins.gradle.util.GradleTaskData

interface GradleTasksIndices {

  /**
   * Finds all tasks which are accessible from Gradle module with module path.
   */
  fun findTasks(modulePath: String): List<GradleTaskData>

  /**
   * Finds all tasks which are accessible from Gradle module with module path.
   *
   * @return
   *  all matched tasks if there are any matched task, otherwise
   *  all partially matched tasks if there are any partially matched task, otherwise
   *  empty list.
   * @see isMatchedTask
   */
  fun findTasks(modulePath: String, matcher: String): List<GradleTaskData>

  /**
   * Batch finds all tasks which are accessible from Gradle module with module path.
   * @see findTasks
   * @see isMatchedTask
   */
  fun findTasks(modulePath: String, matchers: List<String>): List<GradleTaskData>

  /**
   * Checks that [task] can be determined by [matcher] in Gradle module that defined by [modulePath].
   *
   * For example, `smokeTest` task
   *  is matched by [matcher] `smokeTest` and `:smokeTest`,
   *  is partially matched by [matcher] `smoke` and `:smoke`, and
   *  isn't matched by [matcher] `test` and `:test`.
   *
   * @param matcher is task matcher. eg. task, :task, :module:path:task.
   * @return
   *   [ThreeState.YES] if task is matched,
   *   [ThreeState.NO] if task isn't matched,
   *   [ThreeState.UNSURE] if task is partially matched.
   */
  fun isMatchedTask(modulePath: String, task: GradleTaskData, matcher: String): ThreeState

  /**
   * Evaluates all [task] names which can be used from Gradle module that defined by [modulePath].
   * @return empty list if task cannot be runned from defined Gradle module.
   */
  fun getPossibleTaskNames(modulePath: String, task: GradleTaskData): Set<String>

  /**
   * Get ordered all tasks that can be executed from Gradle module that defined by [modulePath].
   */
  fun getTasksCompletionVariances(modulePath: String): Map<String, List<GradleTaskData>>

  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<GradleTasksIndices>()
  }
}