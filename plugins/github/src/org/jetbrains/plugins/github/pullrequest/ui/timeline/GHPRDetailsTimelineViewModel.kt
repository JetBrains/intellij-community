// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.ui.codereview.comment.CodeReviewSubmittableTextViewModelBase
import com.intellij.collaboration.ui.codereview.comment.CodeReviewTextEditingViewModel
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.getOrNull
import com.intellij.collaboration.util.map
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.data.GHReactionContent
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.pullrequest.comment.convertToHtml
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHReactionsService
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.detailsComputationFlow
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsFull
import org.jetbrains.plugins.github.pullrequest.ui.emoji.GHReactionViewModelImpl
import org.jetbrains.plugins.github.pullrequest.ui.emoji.GHReactionsViewModel

class GHPRDetailsTimelineViewModel internal constructor(private val project: Project,
                                                        parentCs: CoroutineScope,
                                                        private val dataContext: GHPRDataContext,
                                                        private val dataProvider: GHPRDataProvider) {
  private val cs = parentCs.childScope()

  private val currentUser: GHUser = dataContext.securityService.currentUser
  private val reactionsService: GHReactionsService = dataContext.reactionsService
  private val reactionIconsProvider: IconsProvider<GHReactionContent> = dataContext.reactionIconsProvider

  val details: StateFlow<ComputedResult<GHPRDetailsFull>> =
    dataProvider.detailsData.detailsComputationFlow.map { it.map(::createDetails) }
      .stateInNow(cs, ComputedResult.loading())

  private val _descriptionEditVm = MutableStateFlow<GHPREditDescriptionViewModel?>(null)
  val descriptionEditVm: StateFlow<GHPREditDescriptionViewModel?> = _descriptionEditVm.asStateFlow()

  private val loadedReactionsState = details.map { it.getOrNull() }.filterNotNull().map { it.reactions }.stateInNow(cs, emptyList())
  val reactionsVm: GHReactionsViewModel =
    GHReactionViewModelImpl(cs, dataProvider.id.id, loadedReactionsState, currentUser, reactionsService, reactionIconsProvider)

  private fun createDetails(data: GHPullRequest): GHPRDetailsFull = GHPRDetailsFull(
    dataProvider.id,
    data.url,
    data.author ?: dataContext.securityService.ghostUser,
    data.createdAt,
    data.title.convertToHtml(project),
    data.body,
    data.body.convertToHtml(project),
    data.viewerCanUpdate,
    data.viewerCanReact,
    data.reactions.nodes
  )

  fun editDescription() {
    if (_descriptionEditVm.value != null) return
    val details = details.value.getOrNull() ?: return
    val description = details.description.orEmpty()
    _descriptionEditVm.value = GHPREditDescriptionViewModel(project, cs, dataProvider.detailsData, description) {
      _descriptionEditVm.update {
        it?.dispose()
        null
      }
    }.apply {
      requestFocus()
    }
  }
}

class GHPREditDescriptionViewModel internal constructor(project: Project,
                                                        parentCs: CoroutineScope,
                                                        private val detailsData: GHPRDetailsDataProvider,
                                                        initialText: String,
                                                        private val onDone: () -> Unit)
  : CodeReviewSubmittableTextViewModelBase(project, parentCs, initialText), CodeReviewTextEditingViewModel {
  override fun save() {
    submit {
      detailsData.updateDetails(description = it)
      onDone()
    }
  }

  override fun stopEditing() = onDone()

  internal fun dispose() = cs.cancel()
}