// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.teamCity

import java.io.PrintStream
import java.util.*

// Functions in this file only handle TeamCity message formatting.

fun PrintStream.reportTestFailure(testName: String, message: String, details: String) {
  val flowId = UUID.randomUUID().toString()
  val escapedTestName = testName.escapeStringForTeamCity()
  val escapedMessage = message.escapeStringForTeamCity()
  val escapedDetails = details.escapeStringForTeamCity()
  println("""
    ##teamcity[testStarted  flowId='$flowId' name='$escapedTestName' nodeId='$escapedTestName' parentNodeId='0']
    ##teamcity[testFailed   flowId='$flowId' name='$escapedTestName' nodeId='$escapedTestName' parentNodeId='0' message='$escapedMessage' details='$escapedDetails']
    ##teamcity[testFinished flowId='$flowId' name='$escapedTestName' nodeId='$escapedTestName' parentNodeId='0']    
  """.trimIndent())
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
