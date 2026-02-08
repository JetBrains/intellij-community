// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.teamCity

import java.io.PrintStream
import java.util.UUID

// Functions in this file only handle TeamCity message formatting.

fun PrintStream.reportTestFailure(testName: String, message: String, details: String) {
  reportTest(testName, message, details, true)
}

fun PrintStream.reportTestSuccess(testName: String, message: String = "", details: String = "") {
  reportTest(testName, message, details, false)
}

private fun PrintStream.reportTest(testName: String, message: String, details: String, isFailure: Boolean) {
  val flowId = UUID.randomUUID().toString()
  val escapedTestName = testName.escapeStringForTeamCity()
  val escapedMessage = message.escapeStringForTeamCity()
  val escapedDetails = details.escapeStringForTeamCity()

  println(buildString {
    appendLine("##teamcity[testStarted  flowId='$flowId' name='$escapedTestName' nodeId='$escapedTestName' parentNodeId='0']")
    if (isFailure) appendLine("##teamcity[testFailed   flowId='$flowId' name='$escapedTestName' nodeId='$escapedTestName' parentNodeId='0' message='$escapedMessage' details='$escapedDetails']")
    appendLine("##teamcity[testFinished flowId='$flowId' name='$escapedTestName' nodeId='$escapedTestName' parentNodeId='0']")
  })
}

fun String.escapeStringForTeamCity(): String {
  return this//.substring(0, kotlin.math.min(7000, this.length))
    .replace("\\|", "||")
    .replace("\\[", "|[")
    .replace("]", "|]")
    .replace("\n", "|n")
    .replace("'", "|'")
    .replace("\r", "|r")
}
