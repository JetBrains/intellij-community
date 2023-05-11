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
    return TestOperationDescriptorImpl(displayName, eventTime, suiteName, className, methodName)
  }

  @JvmStatic
  @Throws(TestEventXmlView.XmlParserException::class, NumberFormatException::class)
  fun convertOperationResult(eventXml: TestEventXmlView): OperationResult {
    val startTime = eventXml.eventTestResultStartTime.toLong()
    val endTime = eventXml.eventTestResultEndTime.toLong()
    val resultType = TestEventResult.fromValue(eventXml.testEventResultType)
    if (resultType == TestEventResult.SUCCESS) {
      return SuccessResultImpl(startTime, endTime, false)
    }
    if (resultType == TestEventResult.SKIPPED) {
      return SkippedResultImpl(startTime, endTime)
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
        val assertionFailure = TestAssertionFailure(
          exceptionName, message, stackTrace, description, emptyList(),
          expectedText, actualText,
          expectedFilePath, actualFilePath
        )
        return FailureResultImpl(startTime, endTime, listOf(assertionFailure))
      }
      if ("assertionFailed" == failureType) {
        val failure = TestFailure(exceptionName, message, stackTrace, description, emptyList(), false)
        return FailureResultImpl(startTime, endTime, listOf(failure))
      }
      if ("error" == failureType) {
        val failure = TestFailure(exceptionName, message, stackTrace, description, emptyList(), true)
        return FailureResultImpl(startTime, endTime, listOf(failure))
      }
      LOG.error("Undefined test failure type: $failureType")
      val failure = FailureImpl(message, stackTrace, emptyList())
      return FailureResultImpl(startTime, endTime, listOf(failure))
    }
    LOG.error("Undefined test result type: $resultType")
    return DefaultOperationResult(startTime, endTime)
  }

  @JvmStatic
  fun decode(s: String): String {
    val bytes = Base64.getDecoder().decode(s)
    return String(bytes, StandardCharsets.UTF_8)
  }
}