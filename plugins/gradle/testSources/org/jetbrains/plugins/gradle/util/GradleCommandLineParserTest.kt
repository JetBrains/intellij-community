// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLine
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineOption
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineTask
import org.junit.jupiter.api.Test

class GradleCommandLineParserTest : GradleCommandLineParserTestCase() {

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
  fun `test --tests option`() {
    GradleCommandLine.parse("task --tests")
      .assertTasksAndArguments(GradleCommandLineTask("task", GradleCommandLineOption.VarargNotation("--tests")))
      .assertScriptParameters()
    GradleCommandLine.parse("task --tests='*'")
      .assertTasksAndArguments(GradleCommandLineTask("task", GradleCommandLineOption.LongNotation("--tests", "'*'")))
      .assertScriptParameters()
    GradleCommandLine.parse("task --tests *")
      .assertTasksAndArguments(GradleCommandLineTask("task", GradleCommandLineOption.VarargNotation("--tests", "*")))
      .assertScriptParameters()
    GradleCommandLine.parse("task --tests 'org.example.TestClass'")
      .assertTasksAndArguments(GradleCommandLineTask("task", GradleCommandLineOption.VarargNotation("--tests", "'org.example.TestClass'")))
      .assertScriptParameters()
    GradleCommandLine.parse("task --tests 'org.example.TestClass.test1' --tests 'org.example.TestClass.test2'")
      .assertTasksAndArguments(
        GradleCommandLineTask("task",
                              GradleCommandLineOption.VarargNotation("--tests", "'org.example.TestClass.test1'"),
                              GradleCommandLineOption.VarargNotation("--tests", "'org.example.TestClass.test2'")
        )
      )
      .assertScriptParameters()
    GradleCommandLine.parse("task --tests org.example.TestClass.test1 --tests=org.example.TestClass.test2")
      .assertTasksAndArguments(
        GradleCommandLineTask("task",
                              GradleCommandLineOption.VarargNotation("--tests", "org.example.TestClass.test1"),
                              GradleCommandLineOption.LongNotation("--tests", "org.example.TestClass.test2")
        )
      )
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

  @Test
  fun `test options with arguments decomposition`() {
    GradleCommandLine.parse("--include-build /home/user/project")
      .assertScriptParameters(GradleCommandLineOption.VarargNotation("--include-build", "/home/user/project"))
    GradleCommandLine.parse("--include-build=/home/user/project")
      .assertScriptParameters(GradleCommandLineOption.LongNotation("--include-build", "/home/user/project"))
    GradleCommandLine.parse("-Dmyprop=myvalue")
      .assertScriptParameters(GradleCommandLineOption.PropertyNotation("-D", "myprop", "myvalue"))
    GradleCommandLine.parse("-Pmyprop=myvalue")
      .assertScriptParameters(GradleCommandLineOption.PropertyNotation("-P", "myprop", "myvalue"))
  }

  @Test
  fun `test command line consistency and ordering`() {
    assertCommandLineConsistency("")
    assertCommandLineConsistency("task task")
    assertCommandLineConsistency("task --tests=test")
    assertCommandLineConsistency("task --tests test1 --tests test2")
    assertCommandLineConsistency("task1 --tests test1 task2 --tests test2")
    assertCommandLineConsistency("-Dmyprop=myvalue")
    assertCommandLineConsistency("--include-build=/home/user/project")
    assertCommandLineConsistency("--include-build /home/user/project")
    assertCommandLineConsistency("--info task", reordered = "task --info")
    assertCommandLineConsistency("--info --debug")
    assertCommandLineConsistency("--debug --info")
  }

  @Test
  fun `test space merging`() {
    GradleCommandLine.parse("")
      .assertTasksAndArguments()
      .assertScriptParameters()
    GradleCommandLine.parse("      ")
      .assertTasksAndArguments()
      .assertScriptParameters()
    GradleCommandLine.parse("  task  task  ")
      .assertTasksAndArguments("task", "task")
      .assertScriptParameters()
    GradleCommandLine.parse(listOf("", "task", "", "", "task", ""))
      .assertTasksAndArguments("task", "task")
      .assertScriptParameters()
    GradleCommandLine.parse("''")
      .assertTasksAndArguments("''")
      .assertScriptParameters()
    GradleCommandLine.parse("\"\"")
      .assertTasksAndArguments("\"\"")
      .assertScriptParameters()
    GradleCommandLine.parse("task --tests ''")
      .assertTasksAndArguments("task", "--tests", "''")
      .assertScriptParameters()
    GradleCommandLine.parse("task --tests=")
      .assertTasksAndArguments("task", "--tests=")
      .assertScriptParameters()
  }
}