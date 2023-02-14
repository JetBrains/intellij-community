// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.util.GradleTaskData
import org.junit.runners.Parameterized

abstract class GradleTasksIndicesTestCase : GradleImportingTestCase() {
  fun findTasks(matcher: String, modulePath: String = "."): List<GradleTaskData> {
    val path = FileUtil.toCanonicalPath("$projectPath/$modulePath")
    val indices = GradleTasksIndices.getInstance(myProject)
    return indices.findTasks(path, matcher)
  }

  fun List<GradleTaskData>.assertTasks(vararg expectedTasks: String) {
    assertEquals(expectedTasks.toSet(), map { it.getFqnTaskName() }.toSet())
  }

  companion object {
    @Parameterized.Parameters(name = "with Gradle-{0}")
    @JvmStatic
    fun tests() = arrayListOf(arrayOf(BASE_GRADLE_VERSION))
  }
}