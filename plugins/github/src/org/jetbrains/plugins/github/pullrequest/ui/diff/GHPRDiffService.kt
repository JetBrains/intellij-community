// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.diff

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.action.ImmutableToolbarLabelAction
import com.intellij.collaboration.ui.codereview.diff.CodeReviewDiffHandlerHelper
import com.intellij.collaboration.util.KeyValuePair
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.tools.combined.CombinedDiffModelImpl
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diff.impl.GenericDataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.pullrequest.comment.action.GHPRDiffReviewThreadsReloadAction
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModel
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowProjectViewModel
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowViewModel

@Service(Service.Level.PROJECT)
internal class GHPRDiffService(private val project: Project, parentCs: CoroutineScope) {
  private val base = CodeReviewDiffHandlerHelper(project, parentCs)

  fun createDiffRequestProcessor(repository: GHRepositoryCoordinates, pullRequest: GHPRIdentifier): DiffRequestProcessor {
    val vm = findDiffVm(project, repository, pullRequest)
    return base.createDiffRequestProcessor(vm, ::createDiffContext)
  }

  fun createCombinedDiffModel(repository: GHRepositoryCoordinates, pullRequest: GHPRIdentifier): CombinedDiffModelImpl {
    val vm = findDiffVm(project, repository, pullRequest)
    return base.createCombinedDiffModel(vm, ::createDiffContext)
  }

  private fun createDiffContext(vm: GHPRDiffViewModel): List<KeyValuePair<*>> = buildList {
    add(KeyValuePair(GHPRDiffViewModel.KEY, vm))
    add(KeyValuePair(DiffUserDataKeys.DATA_PROVIDER, GenericDataProvider().apply {
      putData(GHPRDiffViewModel.DATA_KEY, vm)
      putData(GHPRReviewViewModel.DATA_KEY, vm.reviewVm)
    }))
    add(KeyValuePair(DiffUserDataKeys.CONTEXT_ACTIONS,
                     listOf(ImmutableToolbarLabelAction(CollaborationToolsBundle.message("review.diff.toolbar.label")),
                            GHPRDiffReviewThreadsReloadAction(),
                            ActionManager.getInstance().getAction("Github.PullRequest.Review.Submit"))))
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun findDiffVm(project: Project, repository: GHRepositoryCoordinates, pullRequest: GHPRIdentifier): Flow<GHPRDiffViewModel?> =
  project.serviceIfCreated<GHPRToolWindowViewModel>()?.projectVm?.flatMapLatest {
    if (it?.repository == repository) {
      it.getDiffViewModelFlow(pullRequest)
    }
    else {
      flowOf(null)
    }
  } ?: flowOf(null)

private fun GHPRToolWindowProjectViewModel.getDiffViewModelFlow(pullRequest: GHPRIdentifier): Flow<GHPRDiffViewModel> = channelFlow {
  val acquisitionDisposable = Disposer.newDisposable()
  val vm = acquireDiffViewModel(pullRequest, acquisitionDisposable)
  trySend(vm)
  awaitClose {
    Disposer.dispose(acquisitionDisposable)
  }
}