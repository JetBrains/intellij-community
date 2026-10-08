// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.testAssistant

import com.intellij.openapi.vfs.VirtualFile

internal object TestDataDirectories {
  private const val TEST_DATA_DIRECTORY_NAME: String = "testdata"

  /**
   * Returns `true` if an ancestor of [file], up to and including [contentRoot], is named `testData`, ignoring case.
   */
  fun isInTestDataDirectory(file: VirtualFile, contentRoot: VirtualFile): Boolean {
    return this.hasMatchingParent(file, Int.MAX_VALUE, contentRoot) { name -> name.equals(this.TEST_DATA_DIRECTORY_NAME, ignoreCase = true) }
  }

  /**
   * Returns `true` if the name of one of the [maxDepth] nearest ancestors of [file] contains `testdata`, ignoring case.
   */
  @JvmStatic
  fun isNearTestDataDirectory(file: VirtualFile, maxDepth: Int): Boolean {
    return this.hasMatchingParent(file, maxDepth, null) { name -> name.contains(this.TEST_DATA_DIRECTORY_NAME, ignoreCase = true) }
  }

  private inline fun hasMatchingParent(file: VirtualFile, maxDepth: Int, stopAt: VirtualFile?, matches: (String) -> Boolean): Boolean {
    var depth = 0
    var parent = file.parent
    while (parent != null && depth < maxDepth) {
      if (matches(parent.name)) {
        return true
      }
      if (parent == stopAt) {
        return false
      }
      depth++
      parent = parent.parent
    }
    return false
  }
}
