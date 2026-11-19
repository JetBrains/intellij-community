// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.fileSize
import kotlin.io.path.pathString
import kotlin.io.path.readLines

@TestApplication
internal class IDEACoverageRunnerTest {
  @Test
  fun `local parameter writes the session data path to the options file`(@TempDir dir: Path) {
    val sessionDataPath = dir.resolve("coverage.ic").pathString
    val parameters = SimpleJavaParameters()
    appendCoverage(sessionDataPath, parameters)

    val agent = parameters.targetDependentParameters.toLocalParameters().first()

    assertTrue(agent.startsWith("-javaagent:"), agent)
    assertSessionDataFileIsEmpty(sessionDataPath)
    assertOptionsFileHoldsSessionDataPath(agent, sessionDataPath)
  }

  @Test
  fun `arg file holds the agent and the options file holds the session data path`(@TempDir dir: Path) {
    val sessionDataPath = dir.resolve("coverage.ic").pathString
    val parameters = ArgFileCommandLines.argFileParameters()
    appendCoverage(sessionDataPath, parameters)

    val result = ArgFileCommandLines.build(parameters)

    val agent = result.argFileLines.single { it.startsWith("-javaagent:") }
    val lines = result.argFileLines
    assertTrue(lines.indexOf(agent) < lines.indexOf("-m"), lines.toString())
    assertEquals("hello/hello.Main", lines[lines.indexOf("-m") + 1], lines.toString())
    assertTrue(result.commandLine.none { it.startsWith("-javaagent:") }, result.commandLine.toString())
    assertSessionDataFileIsEmpty(sessionDataPath)
    assertOptionsFileHoldsSessionDataPath(agent, sessionDataPath)
  }

  private fun appendCoverage(sessionDataPath: String, parameters: SimpleJavaParameters) {
    val runner = CoverageRunner.getInstance(IDEACoverageRunner::class.java)
    runner.appendCoverageArgument(sessionDataPath, null, null, parameters, false, true, null, null)
  }

  /**
   * The download of the session data file first creates the file or clears it.
   */
  private fun assertSessionDataFileIsEmpty(sessionDataPath: String) {
    assertEquals(0, Path(sessionDataPath).fileSize())
  }

  /**
   * The agent argument is `-javaagent:<agent>=<options file>`.
   * The download of the session data file writes its path to the options file before the upload of the options file.
   */
  private fun assertOptionsFileHoldsSessionDataPath(agent: String, sessionDataPath: String) {
    val optionsFile = Path(agent.substringAfterLast('='))
    assertEquals(sessionDataPath, optionsFile.readLines().first())
  }
}
