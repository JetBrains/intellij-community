// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.ide.dnd.DnDAction
import com.intellij.ide.dnd.DnDEventImpl
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.LocalFilePath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.jupiter.api.assertNull
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.awt.Point
import java.awt.datatransfer.DataFlavor
import java.io.File

/**
 * A drag out of a read-only changes tree, such as the changes tree of the Log view, reaches an editor or an
 * agent session only if the bean offers a file list flavor.
 * See `FileDropManager.containsFileDropTargets` and `AgentThreadViewFileDropSupport`.
 */
internal class ChangesTreeFileDragBeanTest {
  @Test
  fun `a file is exposed as a local file`() {
    val bean = bean(LocalFilePath("/project/a.md", false))

    assertEquals(listOf(File("/project/a.md")), bean.asFileList())
  }

  @Test
  fun `a directory is skipped`() {
    val bean = bean(LocalFilePath("/project/out", true))

    assertNull(bean.asFileList())
  }

  @Test
  fun `a non-local path is skipped`() {
    val nonLocal = mock<FilePath>()
    `when`(nonLocal.isNonLocal).thenReturn(true)

    assertNull(bean(nonLocal).asFileList())
  }

  @Test
  fun `an empty drag gives null, not an empty list`() {
    // An empty list would reach the drop handlers as a drop of zero files, so null is the correct result.
    assertNull(bean().asFileList())
  }

  @Test
  fun `a drag event offers the file list flavor`() {
    val bean = bean(LocalFilePath("/project/a.md", false))
    val event = DnDEventImpl(null, DnDAction.MOVE, bean, Point(0, 0))

    assertTrue(FileCopyPasteUtil.isFileListFlavorAvailable(event.transferDataFlavors))
    assertEquals(listOf(File("/project/a.md")), event.getTransferData(DataFlavor.javaFileListFlavor))
  }

  private fun bean(vararg paths: FilePath): ChangesTreeFileDragBean = ChangesTreeFileDragBean(paths.asList())
}
