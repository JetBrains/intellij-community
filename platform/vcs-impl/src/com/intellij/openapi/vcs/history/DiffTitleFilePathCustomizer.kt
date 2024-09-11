// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.history

import com.intellij.diff.DiffEditorTitleCustomizer
import com.intellij.diff.impl.ui.FilePathDiffTitleCustomizer
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil

/**
 * @see [DiffUtil.addTitleCustomizers]
 */
object DiffTitleFilePathCustomizer {
  @JvmField
  val EMPTY_CUSTOMIZER = DiffEditorTitleCustomizer { null }

  @JvmStatic
  fun getTitleCustomizers(
    project: Project?,
    beforeRevision: RevisionWithTitle?,
    afterRevision: RevisionWithTitle?,
  ): List<DiffEditorTitleCustomizer> = listOf(
    getTitleCustomizer(beforeRevision, project),
    getTitleCustomizer(afterRevision, project, showPath = beforeRevision == null || beforeRevision.revision.file != afterRevision?.revision?.file),
  )

  @JvmStatic
  fun getTitleCustomizers(beforeFilePath: String?, afterFilePath: String?): List<DiffEditorTitleCustomizer> = listOf(
    beforeFilePath?.let { FilePathDiffTitleCustomizer(beforeFilePath) } ?: EMPTY_CUSTOMIZER,
    afterFilePath?.let { FilePathDiffTitleCustomizer(afterFilePath) } ?: EMPTY_CUSTOMIZER,
  )

  @JvmStatic
  fun getTitleCustomizers(
    project: Project?,
    beforeRevision: RevisionWithTitle?,
    centerRevision: RevisionWithTitle?,
    afterRevision: RevisionWithTitle?,
  ): List<DiffEditorTitleCustomizer> = listOf(
    getTitleCustomizer(beforeRevision, project, showPath = centerRevision == null || centerRevision.revision.file != beforeRevision?.revision?.file),
    getTitleCustomizer(centerRevision, project),
    getTitleCustomizer(afterRevision, project, showPath = centerRevision == null || centerRevision.revision.file != afterRevision?.revision?.file),
  )

  @JvmStatic
  fun getTitleCustomizer(project: Project?, filePath: FilePath, title: String?): DiffEditorTitleCustomizer {
    return FilePathDiffTitleCustomizer(
      displayedPath = getRelativeOrFullPath(project, filePath),
      fullPath = FileUtil.getLocationRelativeToUserHome(FileUtil.toSystemDependentName(filePath.path)),
      revisionLabel = title?.let { FilePathDiffTitleCustomizer.RevisionLabel(title, copiable = false) }
    )
  }

  private fun getTitleCustomizer(
    revisionWithTitle: RevisionWithTitle?,
    project: Project?,
    showPath: Boolean = true,
  ): DiffEditorTitleCustomizer {
    val revisionLabel = revisionWithTitle?.getRevisionLabel()
    return if (revisionWithTitle != null && showPath) FilePathDiffTitleCustomizer(
      displayedPath = getRelativeOrFullPath(project, revisionWithTitle.revision.file),
      fullPath = FileUtil.getLocationRelativeToUserHome(FileUtil.toSystemDependentName(revisionWithTitle.revision.file.path)),
      revisionLabel = revisionLabel,
    )
    else DiffEditorTitleCustomizer { revisionLabel?.createComponent() }
  }

  private fun getRelativeOrFullPath(project: Project?, file: FilePath): String =
    VcsUtil.getPresentablePath(project, file, true, false)

  class RevisionWithTitle(val revision: ContentRevision, title: @NlsSafe String?) {
    private val title: String = title ?: ChangeDiffRequestProducer.getRevisionTitleOrEmpty(revision)

    internal fun getRevisionLabel() = FilePathDiffTitleCustomizer.RevisionLabel(title, revision !is CurrentContentRevision)

    companion object {
      @JvmStatic
      fun create(revision: ContentRevision?, title: String?): RevisionWithTitle? = revision?.let { RevisionWithTitle(it, title) }
    }
  }
}
