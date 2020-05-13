// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history

import com.intellij.testFramework.UsefulTestCase

class FilePredictionHistorySerializationTest : UsefulTestCase() {
  private fun doTest(state: FilePredictionHistoryState) {
    val element = state.serialize()
    val newState = FilePredictionHistoryState().deserialize(element)
    assertEquals(state, newState)
  }

  fun `test serializing state with recent files`() {
    val state = FilePredictionHistoryState()
    state.recentFiles.add(newRecentFile(0, "a"))
    state.recentFiles.add(newRecentFile(1, "b"))
    state.recentFiles.add(newRecentFile(2, "c"))
    doTest(state)
  }

  fun `test serializing state with recent files with prev file`() {
    val state = FilePredictionHistoryState()
    state.recentFiles.add(newRecentFile(0, "a"))
    state.recentFiles.add(newRecentFile(1, "b"))
    state.recentFiles.add(newRecentFile(2, "c"))
    state.prevFile = 2
    doTest(state)
  }

  fun `test serializing usages map`() {
    val state = FilePredictionHistoryState()
    state.root.count = 9
    state.root.usages.put(0, newNode(3, 1 to 1, 2 to 2))
    state.root.usages.put(1, newNode(4, 0 to 3, 2 to 1))
    state.root.usages.put(2, newNode(2, 0 to 2))

    doTest(state)
  }

  fun `test serializing usages map with empty sequence`() {
    val state = FilePredictionHistoryState()
    state.root.count = 9
    state.root.usages.put(0, newNode(3, 1 to 1, 2 to 2))
    state.root.usages.put(1, newNode(4, 0 to 3, 2 to 1))
    state.root.usages.put(2, NGramListNode())

    doTest(state)
  }

  fun `test serializing complete state object`() {
    val state = FilePredictionHistoryState()
    state.recentFiles.add(newRecentFile(0, "a"))
    state.recentFiles.add(newRecentFile(1, "b"))
    state.recentFiles.add(newRecentFile(2, "c"))
    state.prevFile = 2

    state.root.count = 9
    state.root.usages.put(0, newNode(3, 1 to 1, 2 to 2))
    state.root.usages.put(1, newNode(4, 0 to 3, 2 to 1))
    state.root.usages.put(2, newNode(2, 0 to 2))

    doTest(state)
  }

  private fun newNode(count: Int, vararg usages: Pair<Int, Int>): NGramListNode {
    val node = NGramListNode()
    node.count = count
    for (usage in usages) {
      node.usages.put(usage.first, usage.second)
    }
    return node
  }

  private fun newRecentFile(code: Int, url: String): RecentFileEntry {
    val entry = RecentFileEntry()
    entry.code = code
    entry.fileUrl = url
    return entry
  }
}