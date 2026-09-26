// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.ide.dnd.FileFlavorProvider
import com.intellij.openapi.vcs.FilePath
import org.jetbrains.annotations.ApiStatus

/**
 * A drag of files out of a changes tree. The drop target reads the files through the file list flavor, which
 * the platform offers for every [FileFlavorProvider].
 *
 * This is a separate type on purpose. [ChangeListDragBean] carries the Local Changes drop semantics, and a
 * handler such as the Shelf one shelves every change of that bean.
 */
@ApiStatus.Internal
@Suppress("IO_FILE_USAGE")
class ChangesTreeFileDragBean(val filePaths: List<FilePath>) : FileFlavorProvider {
  override fun asFileList(): List<java.io.File>? = localFileList(filePaths)

  companion object {
    /**
     * Maps the dragged paths to local files. This lets a target outside the changes tree accept the drag.
     * An editor, an editor tab, or the file editor splitter accepts a drop only if the transferable
     * offers a file list flavor.
     *
     * Note that the platform offers the file list flavor for every [FileFlavorProvider], before it calls
     * this method. A target can therefore show a drop cursor even when the result is `null`.
     *
     * The method runs on the drag thread, so it must not do blocking IO.
     *
     * @return the local files, or `null` if the drag has none. Do not return an empty list. The platform
     * then reports a drop of zero files, and a handler such as the Markdown one runs an empty write command.
     * `null` makes the platform stop instead.
     */
    @JvmStatic
    fun localFileList(paths: List<FilePath?>): List<java.io.File>? {
      // A directory is not a meaningful drop on an editor, so it stays out of the list.
      val files = paths.mapNotNull { path ->
        path?.takeUnless { it.isNonLocal || it.isDirectory }?.ioFile
      }
      return files.ifEmpty { null }
    }
  }
}
