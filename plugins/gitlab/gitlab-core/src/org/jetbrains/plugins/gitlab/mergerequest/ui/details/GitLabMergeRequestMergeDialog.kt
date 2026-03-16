// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.CommonBundle
import com.intellij.collaboration.ui.CodeReviewUiUtil
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.VerticalComponentGap
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toMutableProperty
import com.intellij.util.ui.JBDimension
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.util.GitLabBundle

/**
 * A modal dialog to get the merge parameters from the user
 */
internal class GitLabMergeRequestMergeDialog(
  private val project: Project,
  mergeRequestDisplayName: @NlsSafe String,
  private val sourceBranchName: @NlsSafe String,
  private val targetBranchName: @NlsSafe String,
  private val needMergeCommit: Boolean,
  mergeCommitMessageDefault: String?,
  removeSourceBranchDefault: Boolean,
  squashCommitsDefault: Boolean,
  private val squashCommitsReadonly: Boolean,
  squashCommitMessageDefault: String?,
) : DialogWrapper(project, false, true) {
  private var mergeCommitMessage: String = mergeCommitMessageDefault ?: ""
  private var removeSourceBranch: Boolean = removeSourceBranchDefault
  private val squashCommitsProp = AtomicBooleanProperty(squashCommitsDefault)
  private var squashCommitMessage: String = squashCommitMessageDefault ?: ""

  init {
    title = GitLabBundle.message("merge.request.merge.dialog.title", mergeRequestDisplayName)
    setOKButtonText(CommonBundle.message("action.text.merge"))
    init()
  }

  override fun createCenterPanel() = panel {
    row {
      val source = HtmlChunk.text(sourceBranchName).bold()
      val target = HtmlChunk.text(targetBranchName).bold()
      text(GitLabBundle.message("merge.request.merge.dialog.description", source, target))
    }
    if (needMergeCommit) {
      row {
        val field = createCommitMessageField(
          project,
          GitLabBundle.message("merge.request.merge.dialog.merge.commit.message.placeholder"),
          mergeCommitMessage
        )
        cell(field)
          .label(GitLabBundle.message("merge.request.merge.dialog.merge.commit.message.label"), LabelPosition.TOP)
          .bind({ it.text }, { field, text -> field.setCommitMessage(text) }, ::mergeCommitMessage.toMutableProperty())
          .resizableColumn()
          .align(Align.FILL)
      }.resizableRow()
    }
    row {
      checkBox(GitLabBundle.message("merge.request.merge.dialog.remove.source"))
        .comment(GitLabBundle.message("merge.request.merge.dialog.remove.source.description", sourceBranchName))
        .bindSelected(::removeSourceBranch)
    }
    row {
      checkBox(GitLabBundle.message("merge.request.merge.dialog.squash"))
        .comment(GitLabBundle.message("merge.request.merge.dialog.squash.description"))
        .bindSelected(squashCommitsProp)
        .enabled(!squashCommitsReadonly)
    }
    row {
      val placeholder = GitLabBundle.message("merge.request.merge.dialog.squash.commit.message.placeholder")
      val field = createCommitMessageField(project, placeholder, squashCommitMessage)

      cell(field)
        .label(GitLabBundle.message("merge.request.merge.dialog.squash.commit.message.label"), LabelPosition.TOP)
        .bind({ it.text }, { field, text -> field.setCommitMessage(text) }, ::squashCommitMessage.toMutableProperty())
        .resizableColumn()
        .align(Align.FILL)

      // change the dialog size to allow for more space
      squashCommitsProp.afterChange { fieldVisible ->
        if (fieldVisible) {
          val currentDialogSize = size
          setSize(currentDialogSize.width, currentDialogSize.height + field.preferredSize.height)
        }
        else {
          val currentDialogSize = size
          setSize(currentDialogSize.width, currentDialogSize.height - field.size.height)
        }
      }
    }.resizableRow().visibleIf(squashCommitsProp)
  }

  fun getData(): Data = Data(
    mergeCommitMessage = mergeCommitMessage,
    removeSourceBranch = removeSourceBranch,
    squashCommits = squashCommitsProp.get(),
    squashCommitMessage = squashCommitMessage
  )

  data class Data(
    val mergeCommitMessage: String,
    val removeSourceBranch: Boolean,
    val squashCommits: Boolean,
    val squashCommitMessage: String,
  )

  private fun createCommitMessageField(project: Project, placeholder: @Nls String, initialMessage: String): CommitMessage =
    CommitMessage(
      project,
      false,
      false,
      true,
      placeholder
    ).apply {
      putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap.BOTH)
      setCommitMessage(initialMessage)
      editorField.addSettingsProvider {
        it.isEmbeddedIntoDialogWrapper = true
        CodeReviewUiUtil.setupStandaloneEditorOutlineBorder(it)
      }
      // to equalize input sizes
      preferredSize = JBDimension(600, 130)
    }.also {
      Disposer.register(disposable, it)
    }
}