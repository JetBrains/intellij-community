// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.task.event.*
import com.intellij.util.text.nullize
import java.nio.charset.StandardCharsets
import java.util.*

object GradleXmlTestEventConverter {

  private val LOG = logger<GradleXmlTestEventConverter>()

  @JvmStatic
  @Throws(TestEventXmlView.XmlParserException::class, NumberFormatException::class)
  fun convertTestDescriptor(eventTime: Long, eventXml: TestEventXmlView): TestOperationDescriptor {
    val suiteName = eventXml.testClassName
    val className = eventXml.testClassName
    val methodName = eventXml.testName
    val displayName = eventXml.testDisplayName
    return TestOperationDescriptor(displayName, eventTime, suiteName, className, methodName)
  }

  @JvmStatic
  @Throws(TestEventXmlView.XmlParserException::class, NumberFormatException::class)
  fun convertOperationResult(eventXml: TestEventXmlView): OperationResult {
    val startTime = eventXml.eventTestResultStartTime.toLong()
    val endTime = eventXml.eventTestResultEndTime.toLong()
    val resultType = TestEventResult.fromValue(eventXml.testEventResultType)
    if (resultType == TestEventResult.SUCCESS) {
      return SuccessResult(startTime, endTime, false)
    }
    if (resultType == TestEventResult.SKIPPED) {
      return SkippedResult(startTime, endTime)
    }
    if (resultType == TestEventResult.FAILURE) {
      val failureType = eventXml.eventTestResultFailureType
      val message = decode(eventXml.eventTestResultErrorMsg) //NON-NLS
      val exceptionName = decode(eventXml.eventTestResultExceptionName)
      val stackTrace = decode(eventXml.eventTestResultStackTrace) //NON-NLS
      val description = decode(eventXml.testEventTestDescription) //NON-NLS
      if ("comparison" == failureType) {
        val actualText = decode(eventXml.eventTestResultActual)
        val expectedText = decode(eventXml.eventTestResultExpected)
        val expectedFilePath = decode(eventXml.eventTestResultFilePath).nullize()
        val actualFilePath = decode(eventXml.eventTestResultActualFilePath).nullize()
        val failure = if (expectedText.isEmpty() && actualText.isEmpty()) {
          TestFailure(exceptionName, message, stackTrace, description, emptyList(), false)
        }
        else {
          TestAssertionFailure(
            exceptionName, message, stackTrace, description, emptyList(),
            expectedText, actualText,
            expectedFilePath, actualFilePath
          )
        }
        return FailureResult(startTime, endTime, listOf(failure))
      }
      if ("assertionFailed" == failureType) {
        val failure = TestFailure(exceptionName, message, stackTrace, description, emptyList(), false)
        return FailureResult(startTime, endTime, listOf(failure))
      }
      if ("error" == failureType) {
        val failure = TestFailure(exceptionName, message, stackTrace, description, emptyList(), true)
        return FailureResult(startTime, endTime, listOf(failure))
      }
      LOG.error("Undefined test failure type: $failureType")
      val failure = Failure(message, stackTrace, emptyList())
      return FailureResult(startTime, endTime, listOf(failure))
    }
    LOG.error("Undefined test result type: $resultType")
    return OperationResult(startTime, endTime)
  }

  @JvmStatic
  fun decode(s: String): String {
    val bytes = Base64.getDecoder().decode(s)
    return String(bytes, StandardCharsets.UTF_8)
  }
}