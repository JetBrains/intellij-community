// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction

import com.intellij.filePrediction.history.FilePredictionHistoryState
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture

class FilePredictionHistoryFeaturesTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {

  private fun doTest(openedFiles: List<String>, size: Int, vararg expected: Pair<String, Int>) {
    val history = FilePredictionHistoryState()
    try {
      for (file in openedFiles) {
        history.onFileOpened(file, 5)
      }

      assertEquals(size, history.size())

      for (pair in expected) {
        assertEquals(pair.second, history.position(pair.first))
      }
    }
    finally {
      history.cleanup()
    }
  }

  fun `test position of the file without history`() {
    doTest(listOf(), 0, "file://a" to -1)
  }

  fun `test position of the new file`() {
    doTest(listOf("file://a"), 1, "file://b" to -1)
  }

  fun `test position of the prev file`() {
    doTest(listOf("file://a"), 1, "file://a" to 0)
  }

  fun `test position of the first file`() {
    doTest(listOf("file://a", "file://b", "file://c"), 3, "file://a" to 2)
  }

  fun `test position of the middle file`() {
    doTest(listOf("file://a", "file://b", "file://c"), 3, "file://b" to 1)
  }

  fun `test position of the latest file`() {
    doTest(listOf("file://a", "file://b", "file://c"), 3, "file://c" to 0)
  }

  fun `test position of the file opened multiple times`() {
    doTest(listOf("file://a", "file://b", "file://a", "file://c"), 3, "file://a" to 1)
  }

  fun `test position of the latest file opened multiple times`() {
    doTest(listOf("file://a", "file://b", "file://a", "file://c", "file://a"), 3, "file://a" to 0)
  }

  fun `test position of the middle file opened multiple times`() {
    doTest(listOf("file://a", "file://b", "file://a", "file://b", "file://a"), 2, "file://b" to 1)
  }

  fun `test recent files history is full`() {
    doTest(
      listOf("file://a", "file://b", "file://c", "file://d", "file://e"), 5,
      "file://a" to 4,
      "file://b" to 3,
      "file://c" to 2,
      "file://d" to 1,
      "file://e" to 0
    )
  }

  fun `test recent files history more than limit`() {
    doTest(
      listOf("file://a", "file://b", "file://c", "file://d", "file://e", "file://f"), 5,
      "file://b" to 4,
      "file://c" to 3,
      "file://d" to 2,
      "file://e" to 1,
      "file://f" to 0
    )
  }

  fun `test recent files history twice more than limit`() {
    doTest(
      listOf("file://a", "file://b", "file://c", "file://d", "file://e", "file://f", "file://g", "file://h", "file://i"), 5,
      "file://e" to 4,
      "file://f" to 3,
      "file://g" to 2,
      "file://h" to 1,
      "file://i" to 0
    )
  }

  fun `test recent files history with repetitions`() {
    doTest(
      listOf("file://a", "file://b", "file://a", "file://d", "file://a", "file://b", "file://c", "file://a", "file://d"), 4,
      "file://b" to 3,
      "file://c" to 2,
      "file://a" to 1,
      "file://d" to 0
    )
  }

  fun `test recent files history with repetitions more than limit`() {
    doTest(
      listOf("file://a", "file://b", "file://a", "file://d", "file://a", "file://e", "file://c", "file://a", "file://d", "file://f"), 5,
      "file://e" to 4,
      "file://c" to 3,
      "file://a" to 2,
      "file://d" to 1,
      "file://f" to 0
    )
  }
}