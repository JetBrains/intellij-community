// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.history

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.annotations.OptionTag

class FilePredictionHistoryState: BaseState() {
  @get:OptionTag
  val recentFiles by list<String>()

  @Synchronized
  fun onFileOpened(fileUrl: String, limit: Int) {
    recentFiles.remove(fileUrl)
    recentFiles.add(fileUrl)

    while (recentFiles.size > limit) {
      recentFiles.removeAt(0)
    }
  }

  @Synchronized
  fun position(fileUrl: String): Int {
    var i = recentFiles.size - 1
    while (i >= 0 && recentFiles[i] != fileUrl) {
      i--
    }
    return if (i < 0) -1 else recentFiles.size - 1 - i
  }

  @Synchronized
  fun size(): Int = recentFiles.size

  @Synchronized
  fun cleanup() = recentFiles.clear()
}