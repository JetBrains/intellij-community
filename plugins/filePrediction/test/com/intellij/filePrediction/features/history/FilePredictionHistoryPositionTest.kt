// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history

class FilePredictionHistoryPositionTest : FilePredictionHistoryBaseTest() {

  private fun doTest(openedFiles: List<String>, size: Int, vararg expected: Pair<String, Int?>) {
    doTestInternal(openedFiles, size, 5) { manager ->
      expected.forEach { assertEquals(it.second, manager.calcHistoryFeatures(it.first).position) }
    }
  }

  private fun doTestCodes(openedFiles: List<String>, size: Int, limit: Int, vararg expected: Pair<String, Int?>) {
    doTestInternal(openedFiles, size, limit) { manager ->
      val state = manager.getState()
      for ((url, expectedCode) in expected) {
        assertEquals(expectedCode, state.findCode(url))
      }
    }
  }

  private fun FilePredictionHistoryState.findCode(url: String): Int? {
    return recentFiles.find { url == it.fileUrl }?.code
  }

  fun `test position of the file without history`() {
    doTest(listOf(), 0, "file://a" to null)
  }

  fun `test position of the new file`() {
    doTest(listOf("file://a"), 1, "file://b" to null)
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

  fun `test small recent file codes`() {
    doTestCodes(
      listOf("file://a", "file://b"), 2, 5,
      "file://a" to 0,
      "file://b" to 1
    )
  }

  fun `test recent file codes`() {
    doTestCodes(
      listOf("file://a", "file://b", "file://c", "file://d", "file://e"), 5, 5,
      "file://a" to 0,
      "file://b" to 1,
      "file://c" to 2,
      "file://d" to 3,
      "file://e" to 4
    )
  }

  fun `test recent file codes with repetitions`() {
    doTestCodes(
      listOf("file://a", "file://b", "file://c", "file://a", "file://c", "file://d", "file://c", "file://d", "file://d", "file://e", "file://b"), 5, 5,
      "file://a" to 0,
      "file://b" to 1,
      "file://c" to 2,
      "file://d" to 3,
      "file://e" to 4
    )
  }

  fun `test recent file codes with removed files`() {
    doTestCodes(
      listOf("a", "b", "c", "d", "e", "f", "g"), 5, 5,
      "a" to null,
      "b" to null,
      "c" to 2,
      "d" to 3,
      "e" to 4,
      "f" to 0,
      "g" to 1
    )
  }

  fun `test recent file codes with repetitions and removed files`() {
    doTestCodes(
      listOf("a", "b", "c", "d", "b", "e", "a", "f", "g"), 5, 5,
      "a" to 0,
      "b" to 1,
      "c" to null,
      "d" to null,
      "e" to 4,
      "f" to 2,
      "g" to 3
    )
  }

  fun `test long recent codes sequence`() {
    doTestCodes(
      listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o"), 15, 15,
      "a" to 0,
      "b" to 1,
      "c" to 2,
      "d" to 3,
      "e" to 4,
      "f" to 5,
      "g" to 6,
      "h" to 7,
      "i" to 8,
      "j" to 9,
      "k" to 10,
      "l" to 11,
      "m" to 12,
      "n" to 13,
      "o" to 14
    )
  }

  fun `test long recent codes sequence with repetitions`() {
    doTestCodes(
      listOf("a", "b", "c", "b", "d", "e", "c", "b", "a", "c", "b", "f", "g", "h", "i", "j", "c",
             "a", "a", "k", "b", "a", "g", "c", "l", "m", "e", "a", "n", "e", "b", "o", "e", "g"), 15, 15,
      "a" to 0,
      "b" to 1,
      "c" to 2,
      "d" to 3,
      "e" to 4,
      "f" to 5,
      "g" to 6,
      "h" to 7,
      "i" to 8,
      "j" to 9,
      "k" to 10,
      "l" to 11,
      "m" to 12,
      "n" to 13,
      "o" to 14
    )
  }
}