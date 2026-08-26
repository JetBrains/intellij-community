// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.ide.dnd.DnDAction
import com.intellij.ide.dnd.DnDEventImpl
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.jupiter.api.assertNull
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.awt.Point
import java.awt.datatransfer.DataFlavor
import java.io.File
import javax.swing.JPanel

/**
 * A drag out of the changes tree reaches an editor only if the bean offers a file list flavor.
 * See `FileDropManager.containsFileDropTargets`.
 */
internal class ChangeListDragBeanFileFlavorTest {
  @Test
  fun `unversioned file is exposed as a local file`() {
    val path = LocalFilePath("/project/new.md", false)
    val bean = bean(unversionedFiles = listOf(path))

    assertEquals(listOf(File("/project/new.md")), bean.asFileList())
  }

  @Test
  fun `ignored file is exposed as a local file`() {
    val path = LocalFilePath("/project/out.log", false)
    val bean = bean(ignoredFiles = listOf(path))

    assertEquals(listOf(File("/project/out.log")), bean.asFileList())
  }

  @Test
  fun `modification is exposed through the after revision`() {
    val before = LocalFilePath("/project/a.md", false)
    val after = LocalFilePath("/project/b.md", false)
    val bean = bean(changes = listOf(change(before, after)))

    assertEquals(listOf(File("/project/b.md")), bean.asFileList())
  }

  @Test
  fun `deletion alone gives null, not an empty list`() {
    val bean = bean(changes = listOf(change(LocalFilePath("/project/gone.md", false), null)))

    // An empty list would reach the drop handlers as a drop of zero files, so null is the correct result.
    assertNull(bean.asFileList())
  }

  @Test
  fun `directory is skipped`() {
    val dir = LocalFilePath("/project/out", true)

    assertNull(bean(ignoredFiles = listOf(dir)).asFileList())
  }

  @Test
  fun `non-local path is skipped`() {
    val nonLocal = mock<FilePath>()
    `when`(nonLocal.isNonLocal).thenReturn(true)

    assertNull(bean(unversionedFiles = listOf(nonLocal)).asFileList())
  }

  @Test
  fun `drag event offers the file list flavor`() {
    val bean = bean(unversionedFiles = listOf(LocalFilePath("/project/new.md", false)))
    val event = DnDEventImpl(null, DnDAction.MOVE, bean, Point(0, 0))

    assertTrue(FileCopyPasteUtil.isFileListFlavorAvailable(event.transferDataFlavors))
    assertEquals(listOf(File("/project/new.md")), event.getTransferData(DataFlavor.javaFileListFlavor))
  }

  private fun bean(
    changes: List<Change> = emptyList(),
    unversionedFiles: List<FilePath> = emptyList(),
    ignoredFiles: List<FilePath> = emptyList(),
  ): ChangeListDragBean = ChangeListDragBean(JPanel(), changes, unversionedFiles, ignoredFiles)

  private fun change(before: FilePath?, after: FilePath?): Change = Change(revision(before), revision(after))

  private fun revision(path: FilePath?): ContentRevision? {
    if (path == null) return null
    val revision = mock<ContentRevision>()
    `when`(revision.file).thenReturn(path)
    return revision
  }
}
