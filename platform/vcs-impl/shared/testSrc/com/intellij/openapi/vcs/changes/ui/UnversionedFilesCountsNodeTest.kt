// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

internal class UnversionedFilesCountsNodeTest {
  @Test
  fun `counts produce a collapsed node without file children`() {
    val node = ChangesBrowserUnversionedFilesNode(mock(), 200_000, 30)

    assertEquals(200_000, node.fileCount)
    assertEquals(30, node.directoryCount)
    assertEquals(0, node.childCount)
    assertTrue(node.isManyFiles)
    assertTrue(node.isMeaningfulNode)
    assertEquals(ChangesBrowserNode.UNVERSIONED_FILES_TAG, node.userObject)
  }

  @Test
  fun `a count node keeps the backend collapse decision for a small count`() {
    val node = ChangesBrowserUnversionedFilesNode(mock(), 0, 3)

    assertEquals(0, node.fileCount)
    assertEquals(3, node.directoryCount)
    assertTrue(node.isManyFiles)
    assertEquals(0, node.childCount)
  }
}
