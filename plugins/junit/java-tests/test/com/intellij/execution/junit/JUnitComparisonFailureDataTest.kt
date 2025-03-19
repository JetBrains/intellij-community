// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit

import com.intellij.rt.execution.junit.ComparisonFailureData
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.io.path.writeBytes

class JUnitComparisonFailureDataTest {
  
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `test empty throwable`() {
    val exception = Throwable()
    val comparisonData = ComparisonFailureData.create(exception)
    Assertions.assertNull(comparisonData)
  }

  @Test
  fun `test empty assertion error`() {
    val exception = java.lang.AssertionError()
    val comparisonData = ComparisonFailureData.create(exception)
    Assertions.assertNull(comparisonData)
  }

  @Test
  fun `test comparison failure (Junit 3)`() {
    val expectedText = "expected text"
    val actualText = "actual text"

    run {
      val exception = junit.framework.ComparisonFailure("message", expectedText, actualText)
      val comparisonData = ComparisonFailureData.create(exception)
      Assertions.assertNotNull(comparisonData)
      Assertions.assertEquals(expectedText, comparisonData.expected)
      Assertions.assertEquals(actualText, comparisonData.actual)
      Assertions.assertNull(comparisonData.expectedFilePath)
      Assertions.assertNull(comparisonData.actualFilePath)
    }

    run {
      val exception = CustomJunit3ComparisonFailure("message", expectedText, actualText)
      val comparisonData = ComparisonFailureData.create(exception)
      Assertions.assertNotNull(comparisonData)
      Assertions.assertEquals(expectedText, comparisonData.expected)
      Assertions.assertEquals(actualText, comparisonData.actual)
      Assertions.assertNull(comparisonData.expectedFilePath)
      Assertions.assertNull(comparisonData.actualFilePath)
    }
  }

  @Test
  fun `test comparison failure (Junit 4)`() {
    val expectedText = "expected text"
    val actualText = "actual text"

    run {
      val exception = org.junit.ComparisonFailure("message", expectedText, actualText)
      val comparisonData = ComparisonFailureData.create(exception)
      Assertions.assertNotNull(comparisonData)
      Assertions.assertEquals(expectedText, comparisonData.expected)
      Assertions.assertEquals(actualText, comparisonData.actual)
      Assertions.assertNull(comparisonData.expectedFilePath)
      Assertions.assertNull(comparisonData.actualFilePath)
    }

    run {
      val exception = CustomJunit4ComparisonFailure("message", expectedText, actualText)
      val comparisonData = ComparisonFailureData.create(exception)
      Assertions.assertNotNull(comparisonData)
      Assertions.assertEquals(expectedText, comparisonData.expected)
      Assertions.assertEquals(actualText, comparisonData.actual)
      Assertions.assertNull(comparisonData.expectedFilePath)
      Assertions.assertNull(comparisonData.actualFilePath)
    }
  }

  @Test
  fun `test comparison failure (OpenTest4j)`() {
    val expectedText = "expected text"
    val actualText = "actual text"
    val expected = org.opentest4j.ValueWrapper.create(expectedText)
    val actual = org.opentest4j.ValueWrapper.create(actualText)

    run {
      val exception = org.opentest4j.AssertionFailedError("message", expected, actual)
      val comparisonData = ComparisonFailureData.create(exception)
      Assertions.assertNotNull(comparisonData)
      Assertions.assertEquals(expectedText, comparisonData.expected)
      Assertions.assertEquals(actualText, comparisonData.actual)
      Assertions.assertNull(comparisonData.expectedFilePath)
      Assertions.assertNull(comparisonData.actualFilePath)
    }

    run {
      val exception = org.opentest4j.AssertionFailedError("message", expectedText, actual)
      val comparisonData = ComparisonFailureData.create(exception)
      Assertions.assertNotNull(comparisonData)
      Assertions.assertEquals(expectedText, comparisonData.expected)
      Assertions.assertEquals(actualText, comparisonData.actual)
      Assertions.assertNull(comparisonData.expectedFilePath)
      Assertions.assertNull(comparisonData.actualFilePath)
    }

    run {
      val exception = org.opentest4j.AssertionFailedError("message", expected, actualText)
      val comparisonData = ComparisonFailureData.create(exception)
      Assertions.assertNotNull(comparisonData)
      Assertions.assertEquals(expectedText, comparisonData.expected)
      Assertions.assertEquals(actualText, comparisonData.actual)
      Assertions.assertNull(comparisonData.expectedFilePath)
      Assertions.assertNull(comparisonData.actualFilePath)
    }

    run {
      val exception = org.opentest4j.AssertionFailedError("message", expectedText, actualText)
      val comparisonData = ComparisonFailureData.create(exception)
      Assertions.assertNotNull(comparisonData)
      Assertions.assertEquals(expectedText, comparisonData.expected)
      Assertions.assertEquals(actualText, comparisonData.actual)
      Assertions.assertNull(comparisonData.expectedFilePath)
      Assertions.assertNull(comparisonData.actualFilePath)
    }

    run {
      val exception = org.opentest4j.AssertionFailedError()
      val comparisonData = ComparisonFailureData.create(exception)
      Assertions.assertNull(comparisonData)
    }

    run {
      val exception = CustomJunit5AssertionFailedError("message", expected, actual)
      val comparisonData = ComparisonFailureData.create(exception)
      Assertions.assertNotNull(comparisonData)
      Assertions.assertEquals(expectedText, comparisonData.expected)
      Assertions.assertEquals(actualText, comparisonData.actual)
      Assertions.assertNull(comparisonData.expectedFilePath)
      Assertions.assertNull(comparisonData.actualFilePath)
    }

    run {
      val exception = CustomJunit5AssertionFailedError("message", expectedText, actual)
      val comparisonData = ComparisonFailureData.create(exception)
      Assertions.assertNotNull(comparisonData)
      Assertions.assertEquals(expectedText, comparisonData.expected)
      Assertions.assertEquals(actualText, comparisonData.actual)
      Assertions.assertNull(comparisonData.expectedFilePath)
      Assertions.assertNull(comparisonData.actualFilePath)
    }

    run {
      val exception = CustomJunit5AssertionFailedError("message", expected, actualText)
      val comparisonData = ComparisonFailureData.create(exception)
      Assertions.assertNotNull(comparisonData)
      Assertions.assertEquals(expectedText, comparisonData.expected)
      Assertions.assertEquals(actualText, comparisonData.actual)
      Assertions.assertNull(comparisonData.expectedFilePath)
      Assertions.assertNull(comparisonData.actualFilePath)
    }

    run {
      val exception = CustomJunit5AssertionFailedError("message", expectedText, actualText)
      val comparisonData = ComparisonFailureData.create(exception)
      Assertions.assertNotNull(comparisonData)
      Assertions.assertEquals(expectedText, comparisonData.expected)
      Assertions.assertEquals(actualText, comparisonData.actual)
      Assertions.assertNull(comparisonData.expectedFilePath)
      Assertions.assertNull(comparisonData.actualFilePath)
    }

    run {
      val exception = CustomJunit5AssertionFailedError()
      val comparisonData = ComparisonFailureData.create(exception)
      Assertions.assertNull(comparisonData)
    }
  }

  @Test
  fun `test comparison failure with file info (OpenTest4j)`() {
    val expectedText = "expected text"
    val actualText = "actual text"
    val expectedBytes = expectedText.toByteArray(StandardCharsets.UTF_8)
    val actualBytes = actualText.toByteArray(StandardCharsets.UTF_8)
    val expectedFilePath = tempDir.resolve("expected.txt")
    val actualFilePath = tempDir.resolve("actual.txt")
    val expectedFileInfo = org.opentest4j.FileInfo(expectedFilePath.pathString, expectedBytes)
    val actualFileInfo = org.opentest4j.FileInfo(actualFilePath.pathString, actualBytes)
    val expected = org.opentest4j.ValueWrapper.create(expectedFileInfo)
    val actual = org.opentest4j.ValueWrapper.create(actualFileInfo)

    expectedFilePath.writeBytes(expectedBytes)
    actualFilePath.writeBytes(actualBytes)

    run {
      val exception = org.opentest4j.AssertionFailedError("message", expected, actual)
      val comparisonData = ComparisonFailureData.create(exception)
      Assertions.assertNotNull(comparisonData)
      Assertions.assertEquals(expectedText, comparisonData.expected)
      Assertions.assertEquals(actualText, comparisonData.actual)
      Assertions.assertEquals(expectedFilePath.pathString, comparisonData.expectedFilePath)
      Assertions.assertEquals(actualFilePath.pathString, comparisonData.actualFilePath)
    }

    run {
      val exception = org.opentest4j.AssertionFailedError("message", expected, actualText)
      val comparisonData = ComparisonFailureData.create(exception)
      Assertions.assertNotNull(comparisonData)
      Assertions.assertEquals(expectedText, comparisonData.expected)
      Assertions.assertEquals(actualText, comparisonData.actual)
      Assertions.assertEquals(expectedFilePath.pathString, comparisonData.expectedFilePath)
      Assertions.assertNull(comparisonData.actualFilePath)
    }

    run {
      val exception = org.opentest4j.AssertionFailedError("message", expectedText, actual)
      val comparisonData = ComparisonFailureData.create(exception)
      Assertions.assertNotNull(comparisonData)
      Assertions.assertEquals(expectedText, comparisonData.expected)
      Assertions.assertEquals(actualText, comparisonData.actual)
      Assertions.assertNull(comparisonData.expectedFilePath)
      Assertions.assertEquals(actualFilePath.pathString, comparisonData.actualFilePath)
    }
  }

  @Test
  @Suppress("DEPRECATION")
  fun `test file comparison failure (IJ Junit 4)`() {
    val expectedText = "expected text"
    val actualText = "actual text"
    val expectedBytes = expectedText.toByteArray(StandardCharsets.UTF_8)
    val actualBytes = actualText.toByteArray(StandardCharsets.UTF_8)
    val expectedFilePath = tempDir.resolve("expected.txt")
    val actualFilePath = tempDir.resolve("actual.txt")

    expectedFilePath.writeBytes(expectedBytes)
    actualFilePath.writeBytes(actualBytes)

    run {
      val exception = com.intellij.rt.execution.junit.FileComparisonFailure(
        "message", expectedText, actualText,
        expectedFilePath.pathString, actualFilePath.pathString
      )
      val comparisonData = ComparisonFailureData.create(exception)
      Assertions.assertNotNull(comparisonData)
      Assertions.assertEquals(expectedText, comparisonData.expected)
      Assertions.assertEquals(actualText, comparisonData.actual)
      Assertions.assertEquals(expectedFilePath.pathString, comparisonData.expectedFilePath)
      Assertions.assertEquals(actualFilePath.pathString, comparisonData.actualFilePath)
    }

    run {
      val exception = com.intellij.rt.execution.junit.FileComparisonFailure(
        "message", expectedText, actualText,
        expectedFilePath.pathString, null
      )
      val comparisonData = ComparisonFailureData.create(exception)
      Assertions.assertNotNull(comparisonData)
      Assertions.assertEquals(expectedText, comparisonData.expected)
      Assertions.assertEquals(actualText, comparisonData.actual)
      Assertions.assertEquals(expectedFilePath.pathString, comparisonData.expectedFilePath)
      Assertions.assertNull(comparisonData.actualFilePath)
    }

    run {
      val exception = com.intellij.rt.execution.junit.FileComparisonFailure(
        "message", expectedText, actualText,
        null, actualFilePath.pathString
      )
      val comparisonData = ComparisonFailureData.create(exception)
      Assertions.assertNotNull(comparisonData)
      Assertions.assertEquals(expectedText, comparisonData.expected)
      Assertions.assertEquals(actualText, comparisonData.actual)
      Assertions.assertNull(comparisonData.expectedFilePath)
      Assertions.assertEquals(actualFilePath.pathString, comparisonData.actualFilePath)
    }

    run {
      val exception = com.intellij.rt.execution.junit.FileComparisonFailure(
        "message", expectedText, actualText,
        null, null
      )
      val comparisonData = ComparisonFailureData.create(exception)
      Assertions.assertNotNull(comparisonData)
      Assertions.assertEquals(expectedText, comparisonData.expected)
      Assertions.assertEquals(actualText, comparisonData.actual)
      Assertions.assertNull(comparisonData.expectedFilePath)
      Assertions.assertNull(comparisonData.actualFilePath)
    }
  }

  @Test
  fun `test file comparison failure (IJ OpenTest4J)`() {
    val expectedText = "expected text"
    val actualText = "actual text"
    val expectedBytes = expectedText.toByteArray(StandardCharsets.UTF_8)
    val actualBytes = actualText.toByteArray(StandardCharsets.UTF_8)
    val expectedFilePath = tempDir.resolve("expected.txt")
    val actualFilePath = tempDir.resolve("actual.txt")

    expectedFilePath.writeBytes(expectedBytes)
    actualFilePath.writeBytes(actualBytes)

    run {
      val exception = com.intellij.platform.testFramework.core.FileComparisonFailedError(
        "message", expectedText, actualText,
        expectedFilePath.pathString, actualFilePath.pathString
      )
      val comparisonData = ComparisonFailureData.create(exception)
      Assertions.assertNotNull(comparisonData)
      Assertions.assertEquals(expectedText, comparisonData.expected)
      Assertions.assertEquals(actualText, comparisonData.actual)
      Assertions.assertEquals(expectedFilePath.pathString, comparisonData.expectedFilePath)
      Assertions.assertEquals(actualFilePath.pathString, comparisonData.actualFilePath)
    }

    run {
      val exception = com.intellij.platform.testFramework.core.FileComparisonFailedError(
        "message", expectedText, actualText,
        expectedFilePath.pathString, null
      )
      val comparisonData = ComparisonFailureData.create(exception)
      Assertions.assertNotNull(comparisonData)
      Assertions.assertEquals(expectedText, comparisonData.expected)
      Assertions.assertEquals(actualText, comparisonData.actual)
      Assertions.assertEquals(expectedFilePath.pathString, comparisonData.expectedFilePath)
      Assertions.assertNull(comparisonData.actualFilePath)
    }

    run {
      val exception = com.intellij.platform.testFramework.core.FileComparisonFailedError(
        "message", expectedText, actualText,
        null, actualFilePath.pathString
      )
      val comparisonData = ComparisonFailureData.create(exception)
      Assertions.assertNotNull(comparisonData)
      Assertions.assertEquals(expectedText, comparisonData.expected)
      Assertions.assertEquals(actualText, comparisonData.actual)
      Assertions.assertNull(comparisonData.expectedFilePath)
      Assertions.assertEquals(actualFilePath.pathString, comparisonData.actualFilePath)
    }

    run {
      val exception = com.intellij.platform.testFramework.core.FileComparisonFailedError(
        "message", expectedText, actualText,
        null, null
      )
      val comparisonData = ComparisonFailureData.create(exception)
      Assertions.assertNotNull(comparisonData)
      Assertions.assertEquals(expectedText, comparisonData.expected)
      Assertions.assertEquals(actualText, comparisonData.actual)
      Assertions.assertNull(comparisonData.expectedFilePath)
      Assertions.assertNull(comparisonData.actualFilePath)
    }
  }

  private class CustomJunit3ComparisonFailure : junit.framework.ComparisonFailure {

    constructor(message: String?, expected: String?, actual: String?) :
      super(message, expected, actual)
  }

  private class CustomJunit4ComparisonFailure : org.junit.ComparisonFailure {

    constructor(message: String?, expected: String?, actual: String?) :
      super(message, expected, actual)
  }

  private class CustomJunit5AssertionFailedError : org.opentest4j.AssertionFailedError {

    constructor() :
      super()

    constructor(message: String?, expected: Any?, actual: Any?) :
      super(message, expected, actual)
  }
}