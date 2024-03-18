// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events

import com.intellij.openapi.externalSystem.model.task.event.TestAssertionFailure
import com.intellij.openapi.externalSystem.model.task.event.TestFailure
import java.nio.file.Path
import kotlin.io.path.readText

object GradleAssertionTestEventConverter {

  @JvmStatic
  fun convertTestFailure(failure: TestFailure): TestFailure {
    val message = failure.message
    val description = failure.description
    val causes = failure.causes
    val exceptionName = failure.exceptionName
    val stackTrace = failure.stackTrace
    val isTestError = failure.isTestError
    val comparisonResult = message?.let { AssertionMessageParser.parse(it) }
    if (failure is TestAssertionFailure) {
      val localizedMessage = if (comparisonResult == null) message else comparisonResult.message
      val expected = convertTestAssertionFailure(failure.expectedText, failure.expectedFile)
      val actual = convertTestAssertionFailure(failure.actualText, failure.actualFile)
      return TestAssertionFailure(
        exceptionName, localizedMessage, stackTrace, description, causes,
        expected.text, actual.text,
        expected.path, actual.path
      )
    }
    else if (comparisonResult != null && failure.causes.isEmpty()) {
      val localizedMessage = comparisonResult.message
      val expected = convertTestAssertionFailure(comparisonResult.expected, null)
      val actual = convertTestAssertionFailure(comparisonResult.actual, null)
      return TestAssertionFailure(
        exceptionName, localizedMessage, stackTrace, description, causes,
        expected.text, actual.text,
        expected.path, actual.path
      )
    }
    else {
      return TestFailure(exceptionName, message, stackTrace, description, causes, isTestError)
    }
  }

  private fun convertTestAssertionFailure(assertionText: String, assertionPath: String?): AssertionValue {
    if (assertionPath != null) {
      return AssertionValue(assertionText, assertionPath)
    }
    val path = AssertionValueParser.parse(assertionText)
    if (path == null) {
      return AssertionValue(assertionText, null)
    }
    val text = runCatching { Path.of(path).readText() }.getOrNull()
    if (text == null) {
      return AssertionValue(assertionText, null)
    }
    return AssertionValue(text, path)
  }

  private class AssertionValue(
    val text: String,
    val path: String?
  )
}