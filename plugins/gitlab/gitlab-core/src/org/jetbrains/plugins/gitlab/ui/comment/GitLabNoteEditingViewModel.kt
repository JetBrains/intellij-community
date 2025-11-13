// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.collaboration.async.cancelAndJoinSilently
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.FocusableViewModel
import com.intellij.collaboration.ui.codereview.comment.CodeReviewSubmittableTextViewModel
import com.intellij.collaboration.ui.codereview.comment.CodeReviewSubmittableTextViewModelBase
import com.intellij.collaboration.ui.codereview.comment.CodeReviewTextEditingViewModel
import com.intellij.collaboration.ui.codereview.comment.submitActionIn
import com.intellij.collaboration.ui.codereview.timeline.thread.CodeReviewTrackableItemViewModel
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.GitLabMergeRequestsPreferences
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabDiscussion
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestNewDiscussionPosition
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.mergerequest.data.MutableGitLabNote
import org.jetbrains.plugins.gitlab.upload.GitLabUploadFileUtil
import org.jetbrains.plugins.gitlab.util.GitLabStatistics
import java.awt.Image
import java.nio.file.Path
import java.util.*
import javax.swing.Action

interface GitLabCodeReviewSubmittableTextViewModel : CodeReviewSubmittableTextViewModel {
  fun uploadFile(path: Path?)
  fun uploadImage(image: Image)
  fun canUploadFile(): Boolean
}

interface GitLabNoteEditingViewModel : GitLabCodeReviewSubmittableTextViewModel {
  suspend fun destroy()

  companion object {
    internal fun forExistingNote(
      parentCs: CoroutineScope,
      project: Project,
      projectData: GitLabProject,
      note: MutableGitLabNote,
      onStopEditing: () -> Unit
    ): GitLabCodeReviewTextEditingViewModel =
      GitLabNoteEditingViewModelImpl(project, parentCs, projectData, note, onStopEditing)

    internal fun forNewNote(
      parentCs: CoroutineScope,
      project: Project,
      projectData: GitLabProject,
      mergeRequest: GitLabMergeRequest,
      currentUser: GitLabUserDTO
    ): NewGitLabNoteViewModel =
      NewStandaloneGitLabNoteViewModel(project, parentCs, "", projectData, mergeRequest, currentUser)

    internal fun forNewDiffNote(
      parentCs: CoroutineScope,
      project: Project,
      projectData: GitLabProject,
      mergeRequest: GitLabMergeRequest,
      currentUser: GitLabUserDTO,
      position: GitLabMergeRequestNewDiscussionPosition
    ): NewGitLabNoteViewModel =
      NewDiffGitLabNoteViewModel(project, parentCs, "", projectData, mergeRequest, currentUser, position)

    internal fun forReplyNote(
      parentCs: CoroutineScope,
      project: Project,
      projectData: GitLabProject,
      discussion: GitLabDiscussion,
      currentUser: GitLabUserDTO
    ): NewGitLabNoteViewModel =
      NewReplyGitLabNoteViewModel(project, parentCs, "", projectData, discussion, currentUser)
  }
}

abstract class AbstractGitLabNoteEditingViewModel(
  override val project: Project,
  val projectData: GitLabProject,
  parentCs: CoroutineScope,
  initialText: String
) : CodeReviewSubmittableTextViewModelBase(project, parentCs, initialText), GitLabNoteEditingViewModel {
  override suspend fun destroy() = cs.cancelAndJoinSilently()

  override fun uploadFile(path: Path?) {
    launchTask {
      GitLabUploadFileUtil.uploadFileAndNotify(project, projectData, path)?.let { fileText ->
        text.update { if (it.isBlank()) fileText else it.trimEnd() + "\n\n" + fileText }
      }
    }
  }

  override fun uploadImage(image: Image) {
    launchTask {
      GitLabUploadFileUtil.uploadImageAndNotify(project, projectData, image)?.let { fileText ->
        text.update { if (it.isBlank()) fileText else it.trimEnd() + "\n\n" + fileText }
      }
    }
  }

  override fun canUploadFile(): Boolean = projectData.canUploadFile()
}

interface GitLabCodeReviewTextEditingViewModel : GitLabCodeReviewSubmittableTextViewModel, CodeReviewTextEditingViewModel

private class GitLabNoteEditingViewModelImpl(
  project: Project, parentCs: CoroutineScope,
  projectData: GitLabProject,
  private val note: MutableGitLabNote,
  private val onStopEditing: () -> Unit,
) : AbstractGitLabNoteEditingViewModel(project, projectData, parentCs, note.body.value), GitLabCodeReviewTextEditingViewModel {
  override fun save() {
    submit {
      note.setBody(it)
      stopEditing()
    }
  }

  override fun stopEditing() {
    onStopEditing()
  }
}

interface NewGitLabNoteViewModel :
  GitLabNoteEditingViewModel,
  CodeReviewTrackableItemViewModel,
  FocusableViewModel {
  val canSubmitAsDraft: Boolean
  val usedAsDraftSubmitActionLast: StateFlow<Boolean>

  val currentUser: GitLabUserDTO

  fun submit()
  fun submitAsDraft()
}

private abstract class NewGitLabNoteViewModelBase(
  project: Project,
  parentCs: CoroutineScope,
  projectData: GitLabProject,
  initialText: String,
  override val currentUser: GitLabUserDTO,
) : AbstractGitLabNoteEditingViewModel(project, projectData, parentCs, initialText), NewGitLabNoteViewModel {
  override val trackingId: String = UUID.randomUUID().toString()

  private val preferences = project.service<GitLabMergeRequestsPreferences>()

  override val usedAsDraftSubmitActionLast: StateFlow<Boolean> = preferences.usedAsDraftSubmitActionLastState

  override fun submit() {
    submit {
      doSubmit(it)
      text.value = ""
      preferences.usedAsDraftSubmitActionLast = false
    }
  }

  protected abstract suspend fun doSubmit(text: String)

  override fun submitAsDraft() {
    require(canSubmitAsDraft) { "Cannot be submitted as draft" }
    submit {
      doSubmitAsDraft(it)
      text.value = ""
      preferences.usedAsDraftSubmitActionLast = true
    }
  }

  protected abstract suspend fun doSubmitAsDraft(text: String)
}

private class NewStandaloneGitLabNoteViewModel(project: Project,
                                               parentCs: CoroutineScope,
                                               initialText: String,
                                               projectData: GitLabProject,
                                               private val mergeRequest: GitLabMergeRequest,
                                               currentUser: GitLabUserDTO)
  : NewGitLabNoteViewModelBase(project, parentCs, projectData, initialText, currentUser) {
  override val canSubmitAsDraft: Boolean = mergeRequest.canAddDraftNotes
  override suspend fun doSubmit(text: String) = mergeRequest.addNote(text)
  override suspend fun doSubmitAsDraft(text: String) = mergeRequest.addDraftNote(text)
}

private class NewDiffGitLabNoteViewModel(project: Project,
                                         parentCs: CoroutineScope,
                                         initialText: String,
                                         projectData: GitLabProject,
                                         private val mergeRequest: GitLabMergeRequest,
                                         currentUser: GitLabUserDTO,
                                         private val position: GitLabMergeRequestNewDiscussionPosition)
  : NewGitLabNoteViewModelBase(project, parentCs, projectData, initialText, currentUser, ) {
  override val canSubmitAsDraft: Boolean = mergeRequest.canAddPositionalDraftNotes
  override suspend fun doSubmit(text: String) = mergeRequest.addNote(position, text)
  override suspend fun doSubmitAsDraft(text: String) = mergeRequest.addDraftNote(position, text)
}

private class NewReplyGitLabNoteViewModel(project: Project,
                                          parentCs: CoroutineScope,
                                          initialText: String,
                                          projectData: GitLabProject,
                                          private val discussion: GitLabDiscussion,
                                          currentUser: GitLabUserDTO)
  : NewGitLabNoteViewModelBase(project, parentCs, projectData, initialText, currentUser, ) {
  override val canSubmitAsDraft: Boolean = discussion.canAddDraftNotes
  override suspend fun doSubmit(text: String) = discussion.addNote(text)
  override suspend fun doSubmitAsDraft(text: String) = discussion.addDraftNote(text)
}

fun CodeReviewSubmittableTextViewModel.onDoneIn(cs: CoroutineScope, callback: suspend () -> Unit) {
  cs.launch {
    state.filter { state ->
      state?.isSuccess == true
    }.collect {
      callback()
    }
  }
}

internal enum class NewGitLabNoteType {
  STANDALONE, DIFF, REPLY
}

private fun NewGitLabNoteType.toStatAction(isDraft: Boolean): GitLabStatistics.MergeRequestAction = when (this) {
  NewGitLabNoteType.STANDALONE -> if (isDraft) GitLabStatistics.MergeRequestAction.ADD_NOTE else GitLabStatistics.MergeRequestAction.ADD_DRAFT_NOTE
  NewGitLabNoteType.DIFF -> if (isDraft) GitLabStatistics.MergeRequestAction.ADD_DIFF_NOTE else GitLabStatistics.MergeRequestAction.ADD_DRAFT_DIFF_NOTE
  NewGitLabNoteType.REPLY -> if (isDraft) GitLabStatistics.MergeRequestAction.ADD_DISCUSSION_NOTE else GitLabStatistics.MergeRequestAction.ADD_DRAFT_DISCUSSION_NOTE
}

internal fun NewGitLabNoteViewModel.submitActionIn(cs: CoroutineScope, actionName: @Nls String,
                                                   project: Project, type: NewGitLabNoteType,
                                                   place: GitLabStatistics.MergeRequestNoteActionPlace): Action =
  submitActionIn(cs, actionName) {
    submit()
    GitLabStatistics.logMrActionExecuted(project, type.toStatAction(false), place)
  }

internal fun NewGitLabNoteViewModel.submitAsDraftActionIn(cs: CoroutineScope, actionName: @Nls String,
                                                          project: Project, type: NewGitLabNoteType,
                                                          place: GitLabStatistics.MergeRequestNoteActionPlace): Action? {
  if (!canSubmitAsDraft) return null
  return submitActionIn(cs, actionName) {
    submitAsDraft()
    GitLabStatistics.logMrActionExecuted(project, type.toStatAction(true), place)
  }
}

internal fun NewGitLabNoteViewModel.primarySubmitActionIn(
  cs: CoroutineScope,
  submit: Action,
  submitAsDraft: Action?
): StateFlow<Action> =
  usedAsDraftSubmitActionLast.mapState(cs) {
    if (it && submitAsDraft != null) submitAsDraft
    else submit
  }

internal fun NewGitLabNoteViewModel.secondarySubmitActionIn(
  cs: CoroutineScope,
  submit: Action,
  submitAsDraft: Action?
): StateFlow<List<Action>> =
  usedAsDraftSubmitActionLast.mapState(cs) {
    if (it && submitAsDraft != null) listOf(submit)
    else listOfNotNull(submitAsDraft)
  }

internal fun NewGitLabNoteViewModel.submitActionHintIn(
  cs: CoroutineScope,
  mainHint: @Nls String,
  draftHint: @Nls String
): StateFlow<@Nls String> =
  if (!canSubmitAsDraft) MutableStateFlow(mainHint)
  else usedAsDraftSubmitActionLast.mapState(cs) { if (it) draftHint else mainHint }
