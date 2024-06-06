// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.diff

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.ui.codereview.comment.CodeReviewSubmittableTextViewModelBase
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.pullrequest.comment.convertToHtml
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.ai.comment.GHPRAIComment
import org.jetbrains.plugins.github.pullrequest.data.ai.comment.mapToLocation
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRAICommentChat
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRAICommentChatMessage
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRAICommentViewModel

class GHPRReviewAICommentDiffViewModel internal constructor(
  private val project: Project,
  parentCs: CoroutineScope,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider,
  private val data: GHPRAIComment,
  change: RefComparisonChange,
  diffData: GitTextFilePatchWithHistory
) : GHPRAICommentViewModel {
  private val cs = parentCs.childScope(javaClass.name)
  private val taskLauncher = SingleCoroutineLauncher(cs)
  private val reviewData = dataProvider.aiReviewData

  private val dataState = MutableStateFlow(data)

  override val key: Any = data.id

  fun getUserIcon(size: Int) = dataContext.avatarIconsProvider.getIcon(dataContext.securityService.currentUser.avatarUrl, size)

  val location: DiffLineLocation? = data.position.mapToLocation(change.revisionNumberAfter.asString(), diffData)
  val isVisible: StateFlow<Boolean> = data.accepted.combineState(data.rejected) { acc, rej ->
    !acc && !rej
  }
  override val textHtml: StateFlow<String> by lazy {
    MutableStateFlow(data.textHtml.convertToHtml(project))
  }

  override val isBusy: StateFlow<Boolean> = taskLauncher.busy

  val chatVm: MutableStateFlow<GHPRAICommentChatViewModel?> = MutableStateFlow(null)

  fun startChat() {
    chatVm.getAndUpdate {
      GHPRAICommentChatViewModel(project, cs) {
        dataProvider.aiReviewData.getChat(data)
      }.also {
        it.newMessageVm.requestFocus()
      }
    }?.destroy()
  }

  override fun accept() {
    taskLauncher.launch {
      reviewData.acceptComment(data)
    }
  }

  override fun reject() {
    taskLauncher.launch {
      reviewData.discardComment(data)
    }
  }

  fun update(data: GHPRAIComment) {
    dataState.value = data
  }
}

class GHPRAICommentChatViewModel(private val project: Project, parentCs: CoroutineScope, chatCreator: suspend () -> GHPRAICommentChat) {
  private val cs = parentCs.childScope()

  private val chatState: StateFlow<GHPRAICommentChat?> = flow {
    val chat = chatCreator()
    emit(chat)
  }.stateIn(cs, SharingStarted.Eagerly, null)

  val messages: SharedFlow<GHPRAICommentChatMessage> = chatState.flatMapLatest {
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

  private inner class EditViewModel(initialText: String)
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
}

class GHPRAIChatNewCommentViewModel(project: Project, cs: CoroutineScope, initialText: String,
                                    private val chatState: StateFlow<GHPRAICommentChat?>)
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
