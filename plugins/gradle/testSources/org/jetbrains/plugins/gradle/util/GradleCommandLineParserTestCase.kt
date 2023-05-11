// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLine
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineOption
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineTask
import org.junit.jupiter.api.Assertions

abstract class GradleCommandLineParserTestCase {

  fun GradleCommandLine.assertTasksAndArguments() = apply {
    Assertions.assertEquals(emptyList<String>(), tasks.tokens)
  }

  fun GradleCommandLine.assertScriptParameters() = apply {
    Assertions.assertEquals(emptyList<String>(), options.tokens)
  }

  fun GradleCommandLine.assertTasksAndArguments(vararg expectedTasksAndArguments: String) = apply {
    Assertions.assertEquals(expectedTasksAndArguments.toList(), tasks.tokens)
  }

  fun GradleCommandLine.assertScriptParameters(vararg expectedScriptParameters: String) = apply {
    Assertions.assertEquals(expectedScriptParameters.toList(), options.tokens)
  }

  fun GradleCommandLine.assertTasksAndArguments(vararg expectedTasks: GradleCommandLineTask) = apply {
    Assertions.assertEquals(expectedTasks.toList(), tasks)
  }

  fun GradleCommandLine.assertScriptParameters(vararg expectedOptions: GradleCommandLineOption) = apply {
    Assertions.assertEquals(expectedOptions.toList(), options)
  }

  fun assertCommandLineConsistency(rawCommandLine: String, reordered: String = rawCommandLine) {
    val commandLine = GradleCommandLine.parse(rawCommandLine)
    Assertions.assertEquals(reordered, commandLine.text)
  }
}