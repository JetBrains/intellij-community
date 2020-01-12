// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.history

import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture

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
}