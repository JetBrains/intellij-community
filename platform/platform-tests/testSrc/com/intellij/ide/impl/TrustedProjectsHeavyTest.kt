// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.TrustedProjectsLocator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.util.io.NioPathPrefixTreeFactory
import com.intellij.testFramework.useProjectAsync
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds

class TrustedProjectsHeavyTest : TrustedProjectsHeavyTestCase() {

  @ParameterizedTest
  @CsvSource(
    "1,      1,       1000,    100",
    "1,      5000,    1000,    100",
    "10,     1000,    1000,    100",
    "100,    100,     1000,    100",
    "1000,   10,      1000,    100",
    "5000,   1,       1000,    100",
  )
  fun `test performance of locating roots of JPS project`(
    numModules: Int,
    numContentRoots: Int,
    numExecutions: Int,
    maxDuration: Int
  ) {
    runBlocking {
      createProjectAsync("project")
        .useProjectAsync { project ->
          testPerformance("Generate modules ${numModules}*$numContentRoots") {
            generateProjectAsync(project, numModules, numContentRoots)
          }
          testPerformance("Collect project roots x$numExecutions", maxDuration.milliseconds) {
            repeat(numExecutions) {
              val locatedProject = TrustedProjectsLocator.locateProject(project)
              Assertions.assertEquals(1, locatedProject.projectRoots.size)
            }
          }
        }
    }
  }

  @ParameterizedTest
  @CsvSource(
    "1,       1,        10000,    1000,    100",
    "1,       10000,    10000,    1000,    100",
    "5,       2000,     10000,    1000,    100",
    "10,      1000,     10000,    1000,    100",
    "100,     100,      10000,    100,     100",
    "1000,    10,       10000,    10,      100",
    "10000,   1,        10000,    1,       100",
  )
  fun `test performance of checking trusted state of roots`(
    numProjects: Int,
    numModules: Int,
    numOtherProjects: Int,
    numExecutions: Int,
    maxDuration: Int
  ) {
    runBlocking {
      val otherProjectRoots = testPerformance("Generate $numOtherProjects other project roots") {
        generatePaths("other-project", numOtherProjects)
      }
      testPerformance("Set trusted state for other-project with ${otherProjectRoots.size} roots") {
        TrustedPaths.getInstance().state = TrustedPaths.State(otherProjectRoots.associate { it.toString() to true })
      }
    }
    runBlocking {
      val projectRoots = testPerformance("Generate $numProjects project roots") {
        generatePaths("project", numProjects)
      }
      val moduleRoots = testPerformance("Generate $numProjects*$numModules module roots") {
        generatePaths(projectRoots, "module", numModules)
      }
      registerProjectLocator(object : TrustedProjectsLocator {
        override fun getProjectRoots(project: Project): List<Path> = projectRoots
        override fun getProjectRoots(projectRoot: Path, project: Project?): List<Path> = projectRoots
      })
      registerProjectLocator(object : TrustedProjectsLocator {
        override fun getProjectRoots(project: Project): List<Path> = moduleRoots
        override fun getProjectRoots(projectRoot: Path, project: Project?): List<Path> = moduleRoots
      })
      val locatedProject = testPerformance("Locate project with ${projectRoots.size} roots") {
        TrustedProjectsLocator.locateProject(projectRoots.first(), null)
      }
      testPerformance("Set trusted state for project with ${projectRoots.size} roots") {
        TrustedProjects.setProjectTrusted(locatedProject, true)
      }
      testPerformance("Check trusted state $numExecutions times", maxDuration.milliseconds) {
        repeat(numExecutions) {
          Assertions.assertTrue(TrustedProjects.isProjectTrusted(locatedProject))
        }
      }
    }
  }

  @Test
  fun `test collecting project roots`() {
    runBlocking {
      registerProjectLocator(object : TrustedProjectsLocator {
        override fun getProjectRoots(projectRoot: Path, project: Project?): List<Path> = emptyList()
        override fun getProjectRoots(project: Project): List<Path> {
          val index = NioPathPrefixTreeFactory.createSet()
          for (module in project.modules) {
            for (contentRoot in module.rootManager.contentRoots) {
              index.add(contentRoot.toNioPath())
            }
          }
          return index.getRoots().toList()
        }
      })
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