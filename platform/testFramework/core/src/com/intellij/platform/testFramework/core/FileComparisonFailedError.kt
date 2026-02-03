// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.core

import com.intellij.rt.execution.junit.FileComparisonData
import org.opentest4j.AssertionFailedError
import org.opentest4j.FileInfo
import org.opentest4j.ValueWrapper
import java.io.File
import java.nio.charset.StandardCharsets

open class FileComparisonFailedError @JvmOverloads constructor(
  message: String?,
  expected: String,
  actual: String,
  expectedFilePath: String? = null,
  actualFilePath: String? = null
) : AssertionFailedError(
  message,
  createFileInfo(expected, expectedFilePath),
  createFileInfo(actual, actualFilePath)
), FileComparisonData {
  init {
    require(expectedFilePath == null || File(expectedFilePath).isFile) {
      "'expectedFilePath' should point to the existing file or be null"
    }
    require(actualFilePath == null || File(actualFilePath).isFile) {
      "'actualFilePath' should point to the existing file or be null"
    }
  }

  override fun getFilePath(): String? = getFilePath(expected)

  override fun getActualFilePath(): String? = getFilePath(actual)

  override fun getActualStringPresentation(): String = getFileText(actual)

  override fun getExpectedStringPresentation(): String = getFileText(expected)
}

private class PresentableFileInfo(
  path: String,
  contents: ByteArray
) : FileInfo(path, contents) {
  override fun toString(): String = getContentsAsString(StandardCharsets.UTF_8)
}

private fun createFileInfo(text: String, path: String?): ValueWrapper {
  val contents = text.toByteArray()
  if (path == null) {
    return ValueWrapper.create(text)
  }
  else {
    val fileInfo = PresentableFileInfo(path, contents)
    return ValueWrapper.create(fileInfo)
  }
}

private fun getFileText(valueWrapper: ValueWrapper): String {
  val value = valueWrapper.value
  if (value is FileInfo) {
    return value.getContentsAsString(StandardCharsets.UTF_8)
  }
  else {
    return value as String
  }
}

private fun getFilePath(valueWrapper: ValueWrapper): String? {
  val value = valueWrapper.value
  return if (value is FileInfo) value.path else null
}