package com.intellij.cce.java.test


import com.intellij.cce.test.TestRunResult
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.jetbrains.idea.maven.execution.MavenRunnerSettings

/**
 * Just a maven runner.
 */
internal object JavaTestRunnerForMaven {

  suspend fun run(project: Project, moduleTests: List<ModuleTests>): TestRunResult {

    val projectDir = project.guessProjectDir()!!

    val params = MavenRunnerParameters(/* isPomExecution = */ true,
                                       /* workingDirPath = */ projectDir.path,
                                       /* pomFileName = */ null,
                                       /* goals = */ listOf("test"),
                                       /* explicitEnabledProfiles = */ emptyList<String>())

    params.cmdOptions = "-am"

    // in multi-module projects tests will be prefixed with module:test
    moduleTests
      .mapNotNull { it.module }
      .also {
        if (it.isNotEmpty()) {
          params.projectsCmdOptionValues = it
        }
      }

    val tests = moduleTests
      .flatMap { it.tests }
      .map {
        val parts = it.split(".")
        // maven runs tests normally only on classes, not methods
        if (parts.lastOrNull()?.firstOrNull()?.isLowerCase() == true) parts.dropLast(1).joinToString(".") else it
      }
      .distinct()

    val runnerSettings = MavenRunnerSettings().also {
      it.mavenProperties = mapOf(
        "surefire.reportFormat" to "plain",
        "surefire.useFile" to "false",
        "surefire.failIfNoSpecifiedTests" to "false",
        "failIfNoTests" to "false",
        "maven.gitcommitid.skip" to "true",
        "test" to tests.joinToString(separator = ",")
      )
    }

    val results = RunConfigurationResults.compute { callback ->
      MavenRunConfigurationType.runConfiguration(project, params, null, runnerSettings, callback)
    }

    val output = results.output
    val compilationSuccessful = MavenOutputParser.compilationSuccessful(output)
    val projectIsResolvable = MavenOutputParser.checkIfProjectIsResolvable(output)
    val (passed, failed) = MavenOutputParser.parse(output)
    return TestRunResult(
      results.exitCode,
      passed,
      failed,
      tests,
      compilationSuccessful,
      projectIsResolvable,
      output
    )
  }
}

object MavenOutputParser {
  private val classNameRegex = Regex("""^.+\((\S+)\)\s+Time elapsed:.*""")
  private val testPrefixes = mutableListOf(" -- in ", " - in ")
  private val errorSubstrings = listOf("ERROR", "FAILURE")
  fun parse(text: String): Pair<List<String>, List<String>> {
    val lines = text.lines()

    val failed = mutableListOf<String>()
    val passed = mutableListOf<String>()
    for (line in lines) {
      val matchResult = classNameRegex.find(line)
      if (matchResult != null) {
        val className = matchResult.groupValues[1]
        if (errorSubstrings.any { line.contains(it) }) {
          failed.add(className)
        }
        else {
          passed.add(className)
        }
      }
    }

    if (passed.isNotEmpty() || failed.isNotEmpty()) {
      return Pair(passed.distinct().filterNot { failed.contains(it) }.sorted(), failed.distinct().sorted())
    }

    return suitBasedParseParse(lines)
  }

  private fun suitBasedParseParse(lines: List<String>): Pair<List<String>, List<String>> {
    val linesWithTests = lines.filter { line ->
      line.contains("Tests run") &&
      testPrefixes.any { line.contains(it) }
    }
    val passed = linesWithTests
      .filter { !errorSubstrings.any(it::contains) }
      .map { trimTestLinePrefix(it) }
      .sorted()
    val failed = linesWithTests
      .filter { errorSubstrings.any(it::contains) }
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