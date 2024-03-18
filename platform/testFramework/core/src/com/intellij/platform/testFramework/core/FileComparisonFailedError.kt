// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.core

import com.intellij.rt.execution.junit.FileComparisonData
import org.opentest4j.AssertionFailedError
import org.opentest4j.FileInfo
import org.opentest4j.ValueWrapper
import java.io.File
import java.nio.charset.StandardCharsets

class FileComparisonFailedError @JvmOverloads constructor(
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

  override fun getFilePath(): String? {
    return getFilePath(expected)
  }

  override fun getActualFilePath(): String? {
    return getFilePath(actual)
  }

  override fun getActualStringPresentation(): String {
    return getFileText(actual)
  }

  override fun getExpectedStringPresentation(): String {
    return getFileText(expected)
  }

  companion object {

    private val CONTENT_CHARSET = StandardCharsets.UTF_8

    private fun createFileInfo(text: String, path: String?): ValueWrapper {
      val contents = text.toByteArray(CONTENT_CHARSET)
      if (path != null) {
        val fileInfo = FileInfo(path, contents)
        return ValueWrapper.create(fileInfo)
      }
      return ValueWrapper.create(text)
    }

    private fun getFileText(valueWrapper: ValueWrapper): String {
      val value: Any = valueWrapper.value
      if (value is FileInfo) {
        return value.getContentsAsString(CONTENT_CHARSET)
      }
      return value as String
    }

    private fun getFilePath(valueWrapper: ValueWrapper): String? {
      val value: Any = valueWrapper.value
      if (value is FileInfo) {
        return value.path
      }
      return null
    }
  }
}