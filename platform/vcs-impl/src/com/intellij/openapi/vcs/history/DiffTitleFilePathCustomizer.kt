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
import com.intellij.ui.components.JBLabel
import com.intellij.vcsUtil.VcsUtil

/**
 * @see [DiffUtil.addTitleCustomizers]
 */
object DiffTitleFilePathCustomizer {
  private val EMPTY_CUSTOMIZER = DiffEditorTitleCustomizer { null }

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

  private fun getTitleCustomizer(
    revision: RevisionWithTitle?,
    project: Project?,
    showPath: Boolean = true,
  ): DiffEditorTitleCustomizer = if (revision != null && showPath) FilePathDiffTitleCustomizer(
    displayedPath = getRelativeOrFullPath(project, revision.revision.file),
    fullPath = FileUtil.getLocationRelativeToUserHome(FileUtil.toSystemDependentName(revision.revision.file.path)),
    label = revision.createLabel(),
  )
  else DiffEditorTitleCustomizer { revision?.createLabel() }

  private fun getRelativeOrFullPath(project: Project?, file: FilePath): String =
    VcsUtil.getPresentablePath(project, file, true, false)

  data class RevisionWithTitle(val revision: ContentRevision, val title: @NlsSafe String?) {
    companion object {
      @JvmStatic
      fun create(revision: ContentRevision?, title: String?): RevisionWithTitle? =
        revision?.let { RevisionWithTitle(it, title) }
    }

    internal fun createLabel(): JBLabel {
      val revisionText = title ?: ChangeDiffRequestProducer.getRevisionTitleOrEmpty(revision)
      return JBLabel(revisionText).setCopyable(revision !is CurrentContentRevision)
    }
  }
}
