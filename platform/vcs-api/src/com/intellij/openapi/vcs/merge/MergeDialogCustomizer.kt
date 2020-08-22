// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.merge

import com.intellij.diff.DiffEditorTitleCustomizer
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Provides custom titles and messages used in the MultipleFileMergeDialog and DiffTools invoked from it.
 */
@ApiStatus.OverrideOnly
open class MergeDialogCustomizer {
  companion object {
    val DEFAULT_CUSTOMIZER_LIST = DiffEditorTitleCustomizerList(null, null, null)
  }

  /**
   * Returns the description that is shown above the list of conflicted files.
   *
   * @param files the files that have conflicted changes and are shown in the dialog.
   */
  open fun getMultipleFileMergeDescription(files: MutableCollection<VirtualFile>): @NlsContexts.Label String = ""

  /**
   * Returns the title of the merge dialog invoked for a 3-way merge of a file (after pressing the "Merge" button).
   *
   * @param file the file that is being merged.
   */
  open fun getMergeWindowTitle(file: VirtualFile): @NlsContexts.DialogTitle String =
    VcsBundle.message("multiple.file.merge.request.title", FileUtil.toSystemDependentName(file.presentableUrl))

  /**
   * Returns the title that is shown above the left panel in the 3-way merge dialog.
   *
   * @param file the file that is being merged.
   * @see getTitleCustomizerList
   */
  open fun getLeftPanelTitle(file: VirtualFile): @NlsContexts.Label String = DiffBundle.message("merge.version.title.our")

  /**
   * Returns the title that is shown above the center panel in the 3-way merge dialog.
   *
   * @param file the file that is being merged.
   * @see getTitleCustomizerList
   */
  open fun getCenterPanelTitle(file: VirtualFile): @NlsContexts.Label String = DiffBundle.message("merge.version.title.base")

  /**
   * Returns the title that is shown above the right panel in the 3-way merge dialog.
   *
   * @param file           the file that is being merged.
   * @param revisionNumber the revision number of the file at the right. Can be null if unknown.
   * @see getTitleCustomizerList
   */
  open fun getRightPanelTitle(file: VirtualFile, revisionNumber: VcsRevisionNumber?): @NlsContexts.Label String =
    if (revisionNumber != null) {
      DiffBundle.message("merge.version.title.their.with.revision", revisionNumber.asString())
    }
    else {
      DiffBundle.message("merge.version.title.their")
    }

  /**
   * Returns the title of the multiple files merge dialog.
   *
   * Don't mix with [getMergeWindowTitle] which is the title of the 3-way merge dialog displayed for a single file.
   */
  open fun getMultipleFileDialogTitle(): @NlsContexts.DialogTitle String = VcsBundle.message("multiple.file.merge.title")

  /**
   * Allows to override the names of the columns of the multiple files merge dialog, defined in [MergeSession.getMergeInfoColumns].
   *
   * Return the column names, matching the order of columns defined in the MergeSession.
   * Return `null` to use names from [MergeSession.getMergeInfoColumns].
   */
  open fun getColumnNames(): List<@NlsContexts.ColumnName String>? = null

  /**
   * Allows to customize diff editor titles in the 3-way merge dialog using [DiffEditorTitleCustomizer] for each editor.
   * This method takes precedence over methods like [getLeftPanelTitle].
   * If [DiffEditorTitleCustomizer] is null for the side, get(side)PanelTitle will be used as a fallback.
   */
  open fun getTitleCustomizerList(file: FilePath): DiffEditorTitleCustomizerList = DEFAULT_CUSTOMIZER_LIST

  data class DiffEditorTitleCustomizerList(
    val leftTitleCustomizer: DiffEditorTitleCustomizer?,
    val centerTitleCustomizer: DiffEditorTitleCustomizer?,
    val rightTitleCustomizer: DiffEditorTitleCustomizer?
  )
}
