// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleCommandLineUtil")

package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLine
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineOption
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineTask


fun parseCommandLine(tasksAndArguments: List<String>, arguments: String?): GradleCommandLine {
  val sortedArguments = GradleCommandLine.parse(arguments ?: "").tokens
  return GradleCommandLine.parse(tasksAndArguments + sortedArguments)
}

fun parseCommandLine(tasksAndArguments: List<String>, arguments: List<String>): GradleCommandLine {
  val sortedArguments = GradleCommandLine.parse(arguments).tokens
  return GradleCommandLine.parse(tasksAndArguments + sortedArguments)
}

fun GradleCommandLineTask.getTestPatterns(): Set<String> {
  return options
    .filter { it.isTestPattern() }
    .flatMap { it.values }
    .toSet()
}

fun GradleCommandLineOption.isTestPattern(): Boolean {
  return GradleConstants.TESTS_ARG_NAME == name
}

fun GradleCommandLineOption.isWildcardTestPattern(): Boolean {
  return isTestPattern() && values.size == 1 && StringUtil.unquoteString(values[0]) == "*"
}
