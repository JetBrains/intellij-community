package com.intellij.cce.java.test


import com.intellij.cce.core.Language
import com.intellij.cce.test.TestRunRequest
import com.intellij.cce.test.TestRunResult
import com.intellij.cce.test.TestRunner
import com.intellij.cce.test.TestRunnerParams
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Key
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.jetbrains.idea.maven.execution.MavenRunnerSettings

private val LOG = fileLogger()

internal class JavaTestRunnerForMaven : TestRunner {
  override fun isApplicable(params: TestRunnerParams): Boolean {
    return params.language == Language.JAVA
           || params.language == Language.KOTLIN // TODO temporary solution for docker testing
  }

  override fun runTests(request: TestRunRequest): TestRunResult {
    LOG.info("Running tests: ${request.tests.joinToString()}")
    if (request.tests.isEmpty()) {
      return TestRunResult(0, emptyList(), emptyList(), true, true, "")
    }

    val project = request.project

    val projectDir = project.guessProjectDir()!!

    val params = MavenRunnerParameters(/* isPomExecution = */ true,
                                       /* workingDirPath = */ projectDir.path,
                                       /* pomFileName = */ "",
                                       /* goals = */ listOf("test"),
                                       /* explicitEnabledProfiles = */ emptyList<String>())
    val deferred = CompletableDeferred<Int>()

    val sb = StringBuilder()

    val callback = ProgramRunner.Callback { descriptor ->
      LOG.info("processStarted $descriptor")
      val processHandler = descriptor.processHandler ?: error("processHandler is null")
      processHandler.addProcessListener(object : ProcessListener {
        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
          sb.append(event.text)
        }

        override fun processTerminated(event: ProcessEvent) {
          LOG.info("processTerminated. exitCode=${event.exitCode}")
          deferred.complete(event.exitCode)
        }

        override fun processNotStarted() {
          LOG.error("processNotStarted")
          deferred.complete(-1)
        }
      })
    }

    // in multi-module projects tests will be prefixed with module:test
    request.tests.map { it.substringBeforeLast(":", "") }
      .filter { it.isNotBlank() }
      .also {
        if (it.isNotEmpty()) {
          params.projectsCmdOptionValues = it
        }
      }

    val runnerSettings = MavenRunnerSettings().also {
      if (request.tests.any()) {
        //todo check
        it.setVmOptions("-Dtest=${request.tests.joinToString(separator = ",") { it.substringAfterLast(":") }}")
      }
    }

    MavenRunConfigurationType.runConfiguration(project,
                                               params,
                                               null,
                                               runnerSettings,
                                               callback)

    LOG.info("await for process termination")
    val exitCode = runBlockingCancellable {
      deferred.await()
    }

    val output = sb.toString()
    val compilationSuccessful = MavenOutputParser.compilationSuccessful(output)
    val projectIsResolvable = MavenOutputParser.checkIfProjectIsResolvable(output)
    val (passed, failed) = MavenOutputParser.parse(output)
    return TestRunResult(exitCode, passed, failed, compilationSuccessful, projectIsResolvable, output)
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