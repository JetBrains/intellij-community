// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history

import com.intellij.filePrediction.history.FileHistoryManager
import com.intellij.filePrediction.history.FilePredictionHistoryState
import com.intellij.filePrediction.history.NGramMapNode
import com.intellij.filePrediction.history.NextFileProbability
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import kotlin.math.abs

abstract class FilePredictionHistoryBaseTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {
  protected fun doTestInternal(openedFiles: List<String>, size: Int, limit: Int, assertion: (FileHistoryManager) -> Unit) {
    val state = FilePredictionHistoryState()
    val manager = FileHistoryManager(state, limit)
    try {
      for (file in openedFiles) {
        manager.onFileOpened(file)
      }

      assertEquals(size, manager.size())
      assertFilesCodes(manager.getState().root.usages.keys(), manager.getState().root)
      assertion.invoke(manager)
    }
    finally {
      manager.cleanup()
    }
  }

  private fun assertFilesCodes(codes: IntArray, root: NGramMapNode) {
    root.usages.forEachEntry { code, value ->
      val keys = value.usages.keys()
      for (key in keys) {
        assertTrue(codes.contains(key))
      }
      true
    }
  }

  protected fun assertNextFileProbabilityEquals(fileName: String, expected: NextFileProbability, actual: NextFileProbability) {
    assertDoubleEquals("MLE for $fileName", expected.mle, actual.mle)
    assertDoubleEquals("min(MLE) for $fileName", expected.minMle, actual.minMle)
    assertDoubleEquals("max(MLE) for $fileName", expected.maxMle, actual.maxMle)
    assertDoubleEquals("MLE/min(MLE) for $fileName", expected.mleToMin, actual.mleToMin)
    assertDoubleEquals("MLE/max(MLE) for $fileName", expected.mleToMax, actual.mleToMax)
  }

  private fun assertDoubleEquals(itemName: String, expected: Double, actual: Double) {
    assertTrue("$itemName isn't equal to expected. Expected: $expected, Actual: $actual", abs(expected - actual) < 0.0000000001)
  }
}