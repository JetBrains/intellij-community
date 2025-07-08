package com.intellij.cce.java.test


import com.intellij.cce.test.TestRunResult
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.jetbrains.idea.maven.execution.MavenRunnerSettings

internal object JavaTestRunnerForMaven {

  suspend fun run(project: Project, moduleTests: List<ModuleTests>): TestRunResult {

    val projectDir = project.guessProjectDir()!!

    val params = MavenRunnerParameters(/* isPomExecution = */ true,
                                       /* workingDirPath = */ projectDir.path,
                                       /* pomFileName = */ "",
                                       /* goals = */ listOf("test"),
                                       /* explicitEnabledProfiles = */ emptyList<String>())

    // in multi-module projects tests will be prefixed with module:test
    moduleTests
      .mapNotNull { it.module }
      .also {
        if (it.isNotEmpty()) {
          params.projectsCmdOptionValues = it
        }
      }

    val runnerSettings = MavenRunnerSettings().also {
      //todo check
      it.setVmOptions("-Dtest=${moduleTests.flatMap { it.tests }.joinToString(separator = ",")}")
    }

    val results = RunConfigurationResults.compute { callback ->
      MavenRunConfigurationType.runConfiguration(project, params, null, runnerSettings, callback)
    }

    val output = results.output
    val compilationSuccessful = MavenOutputParser.compilationSuccessful(output)
    val projectIsResolvable = MavenOutputParser.checkIfProjectIsResolvable(output)
    val (passed, failed) = MavenOutputParser.parse(output)
    return TestRunResult(results.exitCode, passed, failed, compilationSuccessful, projectIsResolvable, output)
  }
}

object MavenOutputParser {
  private val testPrefixes = mutableListOf(" -- in ", " - in ")
  fun parse(text: String): Pair<List<String>, List<String>> {
    val linesWithTests = text.lines().filter { line ->
      line.contains("Tests run") &&
      testPrefixes.any { line.contains(it) }
    }
    val passed = linesWithTests
      .filter { !it.contains("FAILURE") }
      .map { trimTestLinePrefix(it) }
      .sorted()
    val failed = linesWithTests
      .filter { it.contains("FAILURE") }
      .map { trimTestLinePrefix(it) }
      .sorted()
    return Pair(passed, failed)
  }

  fun compilationSuccessful(text: String): Boolean = !text.contains("COMPILATION ERROR")

  fun checkIfProjectIsResolvable(text: String): Boolean =
    !text.contains("[ERROR] Some problems were encountered while processing the POMs")

  private fun trimTestLinePrefix(source: String): String {
    var res = source
    testPrefixes.forEach { prefix ->
      if (res.contains(prefix)) {
        res = res.substring(res.indexOf(prefix)).removePrefix(prefix)
      }
    }
    return res
  }
}