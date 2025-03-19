// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl

import com.intellij.diff.impl.DiffEditorTitleDetails
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.VcsDisposable
import com.intellij.vcsUtil.VcsUtil
import com.intellij.xml.util.XmlStringUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.swing.JComponent
import javax.swing.JTextPane

internal class ContentRevisionLabel(
  val project: Project,
  val coroutineScope: CoroutineScope,
  val contentRevision: ContentRevision,
  val title: @NlsContexts.Label String?,
) : JTextPane(), Disposable {
  private val scope = coroutineScope.childScope("Revision details fetcher")

  init {
    text = title ?: ChangeDiffRequestProducer.getRevisionTitleOrEmpty(contentRevision)
    isEditable = false
    background = null
    border = null
    if (title != null) {
      caret = null
      highlighter = null
    }

    scope.launch { setRevisionTooltip() }
  }

  override fun dispose() {
    scope.cancel()
  }

  private suspend fun setRevisionTooltip() {
    val commitMetadata = DiffRevisionMetadataProvider.EP_NAME.extensionList
      .firstOrNull { it.canApply(contentRevision) }
      ?.getMetadata(project, contentRevision)

    if (commitMetadata != null) {
      val tooltip = VcsBundle.message(
        "diff.title.revision.tooltip",
        VcsUtil.getShortRevisionString(contentRevision.getRevisionNumber()),
        XmlStringUtil.escapeString(VcsUtil.trimCommitMessageToSaneSize(commitMetadata.fullMessage)).replace("\n", UIUtil.BR),
        XmlStringUtil.escapeString(commitMetadata.author.name),
        DateFormatUtil.formatDateTime(commitMetadata.timestamp),
      )

      runInEdt {
        toolTipText = tooltip
      }
    }
  }

  companion object {
    fun createProvider(project: Project, contentRevision: ContentRevision, title: @NlsContexts.Label String?) =
      object : DiffEditorTitleDetails.DetailsLabelProvider {
        override fun createComponent(): JComponent =
          ContentRevisionLabel(project, VcsDisposable.getInstance(project).coroutineScope, contentRevision, title)
      }
  }
}
