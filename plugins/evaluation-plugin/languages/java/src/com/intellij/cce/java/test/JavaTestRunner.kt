package com.intellij.cce.java.test

import com.intellij.cce.core.Language
import com.intellij.cce.test.TestRunRequest
import com.intellij.cce.test.TestRunResult
import com.intellij.cce.test.TestRunner
import com.intellij.cce.test.TestRunnerParams
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.plugins.gradle.util.GradleConstants

internal val LOG = fileLogger()

class JavaTestRunner : TestRunner {
  override fun isApplicable(params: TestRunnerParams): Boolean =
    params.language == Language.JAVA ||
    params.language == Language.KOTLIN

  override suspend fun runTests(request: TestRunRequest): TestRunResult {
    LOG.info("Running tests. total ${request.tests.size}. tests: ${request.tests.joinToString()}")
    if (request.tests.isEmpty()) {
      return TestRunResult(0, emptyList(), emptyList(), true, true, "")
    }

    val moduleTests = request.tests
      .map {
        val parts = it.split(":")
        if (parts.size == 1) null to parts[0]
        else if (parts.size == 2) parts[0] to parts[1]
        else throw IllegalArgumentException("Test name has invalid format: $it")
      }
      .groupBy { it.first }
      .map { ModuleTests(it.key, it.value.map { it.second }) }

    if (isMaven(request.project)) {
      return JavaTestRunnerForMaven.run(request.project, moduleTests)
    }

    if (isGradle(request.project)) {
      return JavaTestRunnerForGradle.run(request.project, moduleTests)
    }

    throw IllegalStateException("Unknown build system. Project: ${request.project.basePath}")
  }
}

internal data class ModuleTests(val module: String?, val tests: List<String>)

private fun isMaven(project: Project): Boolean =
  MavenProjectsManager.getInstance(project).hasProjects()

private fun isGradle(project: Project): Boolean =
  ExternalSystemApiUtil.getSettings(project, GradleConstants.SYSTEM_ID).getLinkedProjectsSettings().isNotEmpty()