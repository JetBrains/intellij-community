// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai.assistedReview

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.ui.codereview.comment.CodeReviewSubmittableTextViewModelBase
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.ai.assistedReview.model.GHPRAIComment
import org.jetbrains.plugins.github.ai.assistedReview.model.accept
import org.jetbrains.plugins.github.ai.assistedReview.model.discard
import org.jetbrains.plugins.github.ai.assistedReview.model.mapToLocation
import org.jetbrains.plugins.github.pullrequest.comment.convertToHtml
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRAICommentViewModel

class GHPRAIReviewCommentDiffViewModel internal constructor(
  private val project: Project,
  parentCs: CoroutineScope,
  private val reviewVm: GHPRAiAssistantReviewViewModel,
  private val data: GHPRAIComment,
  change: RefComparisonChange,
  diffData: GitTextFilePatchWithHistory,
) : GHPRAICommentViewModel {
  private val cs = parentCs.childScope(javaClass.name)
  private val taskLauncher = SingleCoroutineLauncher(cs)

  private val dataState = MutableStateFlow(data)

  override val key: Any = data.id

  override val location: DiffLineLocation? = data.position.mapToLocation(change.revisionNumberAfter.asString(), diffData)
  override val isVisible: StateFlow<Boolean> = data.accepted.combineState(data.rejected) { acc, rej ->
    !acc && !rej
  }
  override val textHtml: StateFlow<String> by lazy {
    MutableStateFlow(data.textHtml.convertToHtml(project))
  }

  override val isBusy: StateFlow<Boolean> = taskLauncher.busy

  override val chatVm: MutableStateFlow<GHPRAICommentChatViewModel?> = MutableStateFlow(null)

  override fun startChat() {
    chatVm.getAndUpdate {
      GHPRAICommentChatViewModel(project, cs) {
        reviewVm.startThreadOnComment(data.id)
      }.also {
        it.newMessageVm.requestFocus()
      }
    }?.destroy()
  }

  override fun accept() {
    data.accept()
  }

  override fun reject() {
    data.discard()
  }

  fun update(data: GHPRAIComment) {
    dataState.value = data
  }
}

class GHPRAICommentChatViewModel(private val project: Project, parentCs: CoroutineScope, chatCreator: suspend () -> GHPRAIReviewCommentChatViewModel) {
  private val cs = parentCs.childScope()

  private val chatState: StateFlow<GHPRAIReviewCommentChatViewModel?> = flow {
    val chat = chatCreator()
    emit(chat)
  }.stateIn(cs, SharingStarted.Eagerly, null)

  @OptIn(ExperimentalCoroutinesApi::class)
  val messages: SharedFlow<GHPRAIReviewCommentChatMessage> = chatState.flatMapLatest {
    it?.messages?.map {
      it.copy(message = it.message.convertToHtml(project))
    } ?: flow {}
  }.shareIn(cs, SharingStarted.Eagerly, Int.MAX_VALUE)
  val newMessageVm: GHPRAIChatNewCommentViewModel = GHPRAIChatNewCommentViewModel(project, cs, "", chatState)

  fun destroy() {
    cs.cancel()
  }

  fun summarize() {
    newMessageVm.summarize()
  }
}

class GHPRAIChatNewCommentViewModel(
  project: Project, cs: CoroutineScope, initialText: String,
  private val chatState: StateFlow<GHPRAIReviewCommentChatViewModel?>,
)
  : CodeReviewSubmittableTextViewModelBase(project, cs, initialText) {
  fun submit() {
    submit { toSubmit ->
      text.value = ""
      chatState.value?.sendMessage(toSubmit)
    }
  }

  fun summarize() {
    submit {
      chatState.value?.summarizeDiscussion()
    }
  }
}
