// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import junit.framework.TestCase.assertEquals
import org.junit.Test

class GradleCommandLineParserTest {
  @Test
  fun `test simple parsing`() {
    GradleCommandLine.parse("")
      .assertTasksAndArguments()
      .assertScriptParameters()
    GradleCommandLine.parse("task")
      .assertTasksAndArguments("task")
      .assertScriptParameters()
    GradleCommandLine.parse("--info")
      .assertTasksAndArguments()
      .assertScriptParameters("--info")
    GradleCommandLine.parse("task --info")
      .assertTasksAndArguments("task")
      .assertScriptParameters("--info")
    GradleCommandLine.parse("task1 task2 --continuous")
      .assertTasksAndArguments("task1", "task2")
      .assertScriptParameters("--continuous")
    GradleCommandLine.parse("-t task1 task2 --no-daemon")
      .assertTasksAndArguments("task1", "task2")
      .assertScriptParameters("-t", "--no-daemon")
    GradleCommandLine.parse("test --tests")
      .assertTasksAndArguments("test", "--tests")
      .assertScriptParameters()
    GradleCommandLine.parse("test --unknown-option")
      .assertTasksAndArguments("test", "--unknown-option")
      .assertScriptParameters()
  }

  @Test
  fun `test options with arguments`() {
    GradleCommandLine.parse("bootRun --include-build /home/user/project")
      .assertTasksAndArguments("bootRun")
      .assertScriptParameters("--include-build", "/home/user/project")
    GradleCommandLine.parse("--include-build /home/user/project bootRun")
      .assertTasksAndArguments("bootRun")
      .assertScriptParameters("--include-build", "/home/user/project")
    GradleCommandLine.parse("bootRun --include-build=/home/user/project")
      .assertTasksAndArguments("bootRun")
      .assertScriptParameters("--include-build=/home/user/project")
    GradleCommandLine.parse("task -Dmyprop=myvalue")
      .assertTasksAndArguments("task")
      .assertScriptParameters("-Dmyprop=myvalue")
    GradleCommandLine.parse("-Pmyprop=myvalue task")
      .assertTasksAndArguments("task")
      .assertScriptParameters("-Pmyprop=myvalue")
  }

  @Test
  fun `test deprecated options parsing`() {
    GradleCommandLine.parse("--help --unknown-option --version")
      .assertTasksAndArguments("--unknown-option")
      .assertScriptParameters("--help", "--version")
    GradleCommandLine.parse("--build-file /home/user/project/build.gradle")
      .assertTasksAndArguments()
      .assertScriptParameters("--build-file", "/home/user/project/build.gradle")
    GradleCommandLine.parse("--settings-file=/home/user/project/settings.gradle")
      .assertTasksAndArguments()
      .assertScriptParameters("--settings-file=/home/user/project/settings.gradle")
  }

  private fun GradleCommandLine.assertTasksAndArguments(vararg expectedTasksAndArguments: String) = apply {
    assertEquals(expectedTasksAndArguments.toList(), tasksAndArguments.tasks.flatMap { listOf(it.name) + it.arguments })
  }

  private fun GradleCommandLine.assertScriptParameters(vararg expectedScriptParameters: String) = apply {
    assertEquals(expectedScriptParameters.toList(), scriptParameters.options)
  }
}