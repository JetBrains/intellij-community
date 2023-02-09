// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.ide.trustedProjects.TrustedProjectsLocator
import com.intellij.testFramework.useProjectAsync
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.time.Duration.Companion.milliseconds

class TrustedProjectsHeavyTest : TrustedProjectsHeavyTestCase() {

  @ParameterizedTest
  @CsvSource(
    "1,      1,      1,      1000,     100",
    "1,      1,      5000,   1000,     100",
    "1,      5000,   1,      1000,     100",
    "5000,   1,      1,      1000,     100",
    "5,      100,    10,     1000,     100"
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
        .useProjectAsync { project ->
          testPerformance("Generate modules x${numProjects}x${numModules}x$numContentRoots") {
            generateProjectAsync(project, numProjects, numModules, numContentRoots)
          }
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
    TrustedProjectsLocator.EP_NAME.point.registerExtension(TestTrustedProjectsLocator(), testDisposable)
    runBlocking {
      createProjectAsync("project")
        .useProjectAsync {
          assertProjectRoots(it, "project")

          createModuleAsync(it, "project", "project")
          assertProjectRoots(it, "project")

          createModuleAsync(it, "project.module", "project/module")
          assertProjectRoots(it, "project")
          createModuleAsync(it, "project.module.main", "project/module/src/main")
          assertProjectRoots(it, "project")
          createModuleAsync(it, "project.module.test", "project/module/src/test")
          assertProjectRoots(it, "project")

          createModuleAsync(it, "module", "module")
          assertProjectRoots(it, "project", "module")
          createModuleAsync(it, "module.main", "module/src/main")
          assertProjectRoots(it, "project", "module")
          createModuleAsync(it, "module.test", "module/src/test")
          assertProjectRoots(it, "project", "module")
        }
    }
  }
}