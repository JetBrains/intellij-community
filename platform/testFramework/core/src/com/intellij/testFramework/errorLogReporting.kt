// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.platform.testFramework.teamCity.TeamCityReporter
import com.intellij.tests.TestLocationStorage
import com.intellij.tests.bazel.SoftAssertFailureXmlReporter
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
fun reportSoftAssertFailure(testName: String, failure: AssertionError) {
  if (!TeamCityLogger.isUnderTC) throw failure

  val kind = TeamCityReporter.SyntheticTestKind.SOFT_ASSERT_FAILURE
  System.getenv("TEST_UNDECLARED_OUTPUTS_DIR")?.let { outputDir ->
    System.err.println(testName)
    failure.printStackTrace(System.err)
    SoftAssertFailureXmlReporter.writeReport(Path.of(outputDir), "(${kind.prefix} $testName)", failure)
    return
  }

  TeamCityReporter.reportTestLifecycle(testName, TeamCityReporter.TestOutcome.FAILED, failure.message.orEmpty(), failure.stackTraceToString(),
                                     generifyTestName = false, syntheticTestKind = kind)
}

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
internal fun logAsTeamcityTestFailure(error: LoggedError) {
  val message = findMessage(error)
  val stackTraceContent = error.stackTraceToString()
  val testName = message ?: "Error logged without message"
  val syntheticTestName = TeamCityReporter.reportTestLifecycle(testName, TeamCityReporter.TestOutcome.FAILED, message ?: "",
                                                               stackTraceContent,
                                                               syntheticTestKind = TeamCityReporter.SyntheticTestKind.IDE_EXCEPTION)
  recordSyntheticTestLocation(syntheticTestName)
}

private var syntheticTestLocationFailed = false

/**
 * Records the synthetic test with the current test class in [TestLocationStorage].
 * The `ResolveTestOwners` build step then attaches the code owner of that class to the synthetic test.
 * Under Bazel, this function records nothing, as the Bazel test runner does.
 * [TestLocationStorage] catches a failed file write.
 * If another failure escapes, this function stops the location recording for the rest of the JVM.
 * The report of the test failures continues.
 */
private fun recordSyntheticTestLocation(syntheticTestName: String) {
  if (syntheticTestLocationFailed || !TeamCityLogger.isUnderTC || System.getenv("TEST_UNDECLARED_OUTPUTS_DIR") != null) return
  val testClass = TestLoggerFactory.getCurrentTestClass() ?: return
  try {
    TestLocationStorage.recordTestLocation(syntheticTestName, testClass, true)
  }
  catch (t: Throwable) {
    rethrowControlFlowException(t)
    syntheticTestLocationFailed = true
    println(t)
  }
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
