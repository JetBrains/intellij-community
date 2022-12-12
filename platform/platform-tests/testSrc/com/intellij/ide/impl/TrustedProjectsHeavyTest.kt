// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.testFramework.closeProjectAsync
import com.intellij.testFramework.useProjectAsync
import com.intellij.testFramework.withProjectAsync
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.time.Duration.Companion.milliseconds

class TrustedProjectsHeavyTest : TrustedProjectsHeavyTestCase() {

  @ParameterizedTest
  @CsvSource(
    "1,      1,      1,      100,     100",
    "1,      1,      5000,   100,     100",
    "1,      5000,   1,      100,     100",
    "1000,   5,      3,      100,     1000"
  )
  fun `test performance of collecting project roots`(
    numProjects: Int,
    numModules: Int,
    numContentRoots: Int,
    numExecutions: Int,
    maxDuration: Int
  ) {
    runBlocking {
      createProjectAsync("project")
        .withProjectAsync { project ->
          testPerformance("Generate modules x${numProjects}x${numModules}x$numContentRoots") {
            generateProjectAsync(project, numProjects, numModules, numContentRoots)
          }
        }
        .useProjectAsync { project ->
          testPerformance("Collect project roots x$numExecutions", maxDuration.milliseconds) {
            repeat(numExecutions) {
              Assertions.assertTrue(project.isTrusted())
            }
          }
        }
    }
  }

  @Test
  fun `test collecting project roots`() {
    runBlocking {
      createProjectAsync("project")
        .withProjectAsync { assertProjectRoots(it, "project") }

        .withProjectAsync { createModuleAsync(it, "project", "project") }
        .withProjectAsync { assertProjectRoots(it, "project") }

        .withProjectAsync { createModuleAsync(it, "project.module", "project/module") }
        .withProjectAsync { assertProjectRoots(it, "project") }
        .withProjectAsync { createModuleAsync(it, "project.module.main", "project/module/src/main") }
        .withProjectAsync { assertProjectRoots(it, "project") }
        .withProjectAsync { createModuleAsync(it, "project.module.test", "project/module/src/test") }
        .withProjectAsync { assertProjectRoots(it, "project") }

        .withProjectAsync { createModuleAsync(it, "module", "module") }
        .withProjectAsync { assertProjectRoots(it, "project", "module") }
        .withProjectAsync { createModuleAsync(it, "module.main", "module/src/main") }
        .withProjectAsync { assertProjectRoots(it, "project", "module") }
        .withProjectAsync { createModuleAsync(it, "module.test", "module/src/test") }
        .withProjectAsync { assertProjectRoots(it, "project", "module") }

        .closeProjectAsync()
    }
  }
}