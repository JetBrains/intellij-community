// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl

import com.intellij.diff.DiffEditorTitleCustomizer
import com.intellij.diff.impl.DiffEditorTitleDetails
import com.intellij.diff.impl.getCustomizers
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer

/**
 * @see [com.intellij.diff.util.DiffUtil.addTitleCustomizers]
 */
object DiffTitleWithDetailsCustomizers {
  @JvmStatic
  fun getTitleCustomizers(
    project: Project?,
    beforeRevision: ContentRevision?,
    beforeTitle: @NlsContexts.Label String?,
    afterRevision: ContentRevision?,
    afterTitle: @NlsContexts.Label String?,
  ): List<DiffEditorTitleCustomizer> = listOf(
    createDiffTitleDetails(project, beforeRevision, beforeTitle, displayPath = true),
    createDiffTitleDetails(project, afterRevision, afterTitle, displayPath = beforeRevision == null || beforeRevision.file != afterRevision?.file),
  ).getCustomizers()

  @JvmStatic
  fun getTitleCustomizers(
    project: Project?,
    leftRevision: ContentRevision?,
    leftTitle: @NlsContexts.Label String?,
    centerRevision: ContentRevision?,
    centerTitle: @NlsContexts.Label String?,
    rightRevision: ContentRevision?,
    rightTitle: @NlsContexts.Label String?,
  ): List<DiffEditorTitleCustomizer> = listOf(
    createDiffTitleDetails(project, leftRevision, leftTitle, displayPath = centerRevision == null || centerRevision.file != leftRevision?.file),
    createDiffTitleDetails(project, centerRevision, centerTitle, displayPath = true),
    createDiffTitleDetails(project, rightRevision, rightTitle, displayPath = centerRevision == null || centerRevision.file != rightRevision?.file),
  ).getCustomizers()

  @JvmStatic
  fun getTitleCustomizers(
    project: Project?,
    change: Change,
    tileBefore: @NlsContexts.Label String?,
    titleAfter: @NlsContexts.Label String?,
  ): List<DiffEditorTitleCustomizer> = getTitleCustomizers(project, change.beforeRevision, tileBefore, change.afterRevision, titleAfter)

  @JvmStatic
  fun getTitleCustomizers(beforeFilePath: @NlsSafe String?, afterFilePath: @NlsSafe String?): List<DiffEditorTitleCustomizer> = listOf(
    DiffEditorTitleDetails.createFromPath(beforeFilePath),
    DiffEditorTitleDetails.createFromPath(afterFilePath),
  ).getCustomizers()

  @JvmStatic
  fun createDiffTitleDetails(
    project: Project?,
    revision: ContentRevision?,
    title: @NlsContexts.Label String?,
    displayPath: Boolean,
  ): DiffEditorTitleDetails {
    val revisionLabel = if (revision != null && project != null) {
      ContentRevisionLabel.createProvider(project, revision, title)
    } else if (title != null) {
      DiffEditorTitleDetails.RevisionLabelProvider(title, copiable = false)
    } else {
      DiffEditorTitleDetails.RevisionLabelProvider(ChangeDiffRequestProducer.getRevisionTitleOrEmpty(revision), copiable = true)
    }

    val pathLabel = if (revision != null && displayPath) DiffEditorTitleDetails.getFilePathLabel(project, revision.file) else null

    return DiffEditorTitleDetails(pathLabel, revisionLabel)
  }
}