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

internal class JavaTestRunnerForMaven: TestRunner {
  override fun isApplicable(params: TestRunnerParams): Boolean {
    return params.language == Language.JAVA
  }

  override fun runTests(request: TestRunRequest): TestRunResult {
    LOG.info("Running tests: ${request.tests.joinToString()}")
    if (request.tests.isEmpty()) {
      return TestRunResult(emptyList(), emptyList(), "")
    }

    val project = request.project

    val projectDir = project.guessProjectDir()!!

    val params = MavenRunnerParameters(/* isPomExecution = */ true,
                                       /* workingDirPath = */ projectDir.path,
                                       /* pomFileName = */ "",
                                       /* goals = */ listOf("test"),
                                       /* explicitEnabledProfiles = */ emptyList<String>())
    val deferred = CompletableDeferred<Unit>()

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
          deferred.complete(Unit)
        }

        override fun processNotStarted() {
          LOG.error("processNotStarted")
          deferred.complete(Unit)
        }
      })
    }
    val runnerSettings = MavenRunnerSettings().also {
      if (request.tests.any()) {
        //todo check
        it.setVmOptions("-Dtest=${request.tests.joinToString(separator = ",")}")
      }
    }

    MavenRunConfigurationType.runConfiguration(project,
                                               params,
                                               null,
                                               runnerSettings,
                                               callback)

    LOG.info("await for process termination")
    runBlockingCancellable {
      deferred.await()
    }

    return MavenOutputParser().parse(sb.toString())
  }
}

class MavenOutputParser {
  fun parse(text: String): TestRunResult {
    val linesWithTests = text.lines().filter { it.contains("Tests run") && it.contains("-- in") }
    val passed = linesWithTests
      .filter { !it.contains("FAILURE") }
      .map { trimTestLinePrefix(it) }
      .sorted()
    val failed = linesWithTests
      .filter { it.contains("FAILURE") }
      .map { trimTestLinePrefix(it) }
      .sorted()
    return TestRunResult(passed, failed, text)
  }

  private fun trimTestLinePrefix(source: String): String {
    return source.substring(source.indexOf("-- in ")).removePrefix("-- in ")
  }
}