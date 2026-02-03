// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("EqualsToFile")
package com.intellij.testFramework

import com.intellij.openapi.util.text.Strings
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import junit.framework.TestCase
import java.io.File

private fun String.trimTrailingWhitespacesAndAddNewlineAtEOF(): String {
  return splitToSequence('\n').joinToString(separator = "\n", transform = String::trimEnd).let { result ->
    if (result.endsWith('\n')) result else result + "\n"
  }
}

fun assertEqualsToFile(description: String, expected: File, actual: String) {
  if (!expected.exists()) {
    expected.writeText(actual)
    TestCase.fail("File didn't exist. New file was created (${expected.canonicalPath}).")
  }

  val expectedText = Strings.convertLineSeparators(expected.readText().trim()).trimTrailingWhitespacesAndAddNewlineAtEOF()
  val actualText = Strings.convertLineSeparators(actual.trim()).trimTrailingWhitespacesAndAddNewlineAtEOF()
  if (expectedText != actualText) {
    throw FileComparisonFailedError(description, expectedText, actualText, expected.absolutePath)
  }
}
