// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.platform.testFramework.teamCity.generifyErrorMessage
import com.intellij.platform.testFramework.teamCity.reportTestFailure

internal fun ErrorLog.reportAsFailures() {
  val errors = takeLoggedErrors()
  for (error in errors) {
    logAsTeamcityTestFailure(error)
  }
}

// Avoid changing the test name!
// TeamCity remembers failures by the test name.
// Changing the test name results in effectively new failed tests being reported,
// so all saved TC data about previous failures will not apply, including muted state and investigations.
// Some exception messages include file names, system hash codes (Object.toString), etc.
// To make the test name stable between different test runs, such data is stripped out before computing the test name.
private fun logAsTeamcityTestFailure(error: LoggedError) {
  val message = findMessage(error)
  val stackTraceContent = error.stackTraceToString()
  val testName = if (message == null) {
    "Error logged without message"
  } else {
    generifyErrorMessage(message)
      .replace("[:.()]".toRegex(), " ")
      .replace(" +".toRegex(), " ")
  }
  System.out.reportTestFailure(testName, message ?: "", stackTraceContent)
}

private fun findMessage(t: Throwable): String? {
  var current: Throwable = t
  while (true) {
    val message = current.message
    if (!message.isNullOrBlank()) {
      return message
    }
    val cause = current.cause
    if (cause == null || cause == current) {
      return null
    }
    current = cause
  }
}
