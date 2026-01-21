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
import java.nio.file.Files
import java.nio.file.Paths

internal val LOG = fileLogger()

class JavaTestRunner : TestRunner {
  override fun isApplicable(params: TestRunnerParams): Boolean =
    params.language == Language.JAVA ||
    params.language == Language.KOTLIN

  override suspend fun runTests(request: TestRunRequest): TestRunResult {
    LOG.info("Running tests. total ${request.tests.size}. tests: ${request.tests.joinToString()}")
    if (request.tests.isEmpty()) {
      return TestRunResult(0, emptyList(), emptyList(), emptyList(), true, true, "")
    }

    val moduleTests = request.tests
      .map { test ->
        val parts = test
          .takeWhile { it != '[' } // test format in swe-polybench for one guava instance
          .split(":")
        if (parts.size == 1) null to parts[0]
        else if (parts.size == 2) parts[0] to parts[1]
        else throw IllegalArgumentException("Test name has invalid format: $test")
      }
      .groupBy { it.first }
      .map {
        val module = if(it.key == "src") null else it.key
        ModuleTests(module, it.value
          .map { it.second }
          .filter { it != "gradle_test_execution" })   // just a command to run all the tests (no specific)
      }

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

internal fun isMaven(project: Project): Boolean =
  MavenProjectsManager.getInstance(project).hasProjects()

internal fun isGradle(project: Project): Boolean {
  val isGradleProjectFound = ExternalSystemApiUtil.getSettings(project, GradleConstants.SYSTEM_ID).getLinkedProjectsSettings().isNotEmpty()
  if (isGradleProjectFound) {
    return isGradleProjectFound
  }
  val isGradleFilePresent = project.basePath?.let {
    Files.exists(Paths.get(it, GradleConstants.DEFAULT_SCRIPT_NAME)) ||
    Files.exists(Paths.get(it, GradleConstants.KOTLIN_DSL_SCRIPT_NAME))
  } ?: false
  if (isGradleFilePresent) {
    LOG.warn("Gradle file is present, but no Gradle project configured in IDEA. " +
             "This might affect the set of tools in MCP available to agent. Project: ${project.basePath}")
  }
  return isGradleFilePresent
}