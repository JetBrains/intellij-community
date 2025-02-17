// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.diff

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.action.ImmutableToolbarLabelAction
import com.intellij.collaboration.ui.codereview.diff.AsyncDiffRequestProcessorFactory
import com.intellij.collaboration.util.KeyValuePair
import com.intellij.collaboration.util.filePath
import com.intellij.collaboration.util.fileStatus
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.tools.combined.CombinedDiffComponentProcessor
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diff.impl.GenericDataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.ui.PresentableChange
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.pullrequest.comment.action.GHPRDiffReviewThreadsReloadAction
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModel
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModel
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowViewModel
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateDiffChangeViewModel
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateDiffViewModel

@Service(Service.Level.PROJECT)
internal class GHPRDiffService(private val project: Project, parentCs: CoroutineScope) {
  private val cs = parentCs.childScope(javaClass.name, Dispatchers.Main)

  fun createDiffRequestProcessor(repository: GHRepositoryCoordinates, pullRequest: GHPRIdentifier): DiffRequestProcessor {
    val vm = findDiffVm(project, repository, pullRequest)
    return AsyncDiffRequestProcessorFactory.createIn(cs, project, vm, ::createDiffContext, ::getChangeDiffVmPresentation)
  }

  fun createCombinedDiffProcessor(repository: GHRepositoryCoordinates, pullRequest: GHPRIdentifier): CombinedDiffComponentProcessor {
    val vm = findDiffVm(project, repository, pullRequest)
    return AsyncDiffRequestProcessorFactory.createCombinedIn(cs, project, vm, ::createDiffContext, ::getChangeDiffVmPresentation)
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

  fun createDiffRequestProcessor(repository: GHRepositoryCoordinates): DiffRequestProcessor {
    val vm = findDiffVm(project, repository)
    return AsyncDiffRequestProcessorFactory.createIn(cs, project, vm, { emptyList() }, ::getChangeDiffVmPresentation)
  }

  fun createCombinedDiffProcessor(repository: GHRepositoryCoordinates): CombinedDiffComponentProcessor {
    val vm = findDiffVm(project, repository)
    return AsyncDiffRequestProcessorFactory.createCombinedIn(cs, project, vm, { emptyList() }, ::getChangeDiffVmPresentation)
  }

  private fun getChangeDiffVmPresentation(changeVm: GHPRDiffChangeViewModel): PresentableChange =
    object : PresentableChange {
      override fun getFilePath(): FilePath = changeVm.change.filePath
      override fun getFileStatus(): FileStatus = changeVm.change.fileStatus
    }

  private fun getChangeDiffVmPresentation(changeVm: GHPRCreateDiffChangeViewModel): PresentableChange =
    object : PresentableChange {
      override fun getFilePath(): FilePath = changeVm.change.filePath
      override fun getFileStatus(): FileStatus = changeVm.change.fileStatus
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

private fun findDiffVm(project: Project, repository: GHRepositoryCoordinates): Flow<GHPRCreateDiffViewModel?> =
  project.serviceIfCreated<GHPRToolWindowViewModel>()?.projectVm?.map {
    if (it?.repository == repository) {
      it.getCreateVmOrNull()?.diffVm
    }
    else null
  } ?: flowOf(null)

private fun GHPRConnectedProjectViewModel.getDiffViewModelFlow(pullRequest: GHPRIdentifier): Flow<GHPRDiffViewModel> = channelFlow {
  val acquisitionDisposable = Disposer.newDisposable()
  val vm = acquireDiffViewModel(pullRequest, acquisitionDisposable)
  trySend(vm)
  awaitClose {
    Disposer.dispose(acquisitionDisposable)
  }
}