// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.action.ImmutableToolbarLabelAction
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.filePath
import com.intellij.collaboration.util.fileStatus
import com.intellij.collaboration.util.selectedChange
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.tools.combined.*
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diff.impl.GenericDataProvider
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.changes.actions.diff.CombinedDiffPreviewModel
import com.intellij.openapi.vcs.history.VcsDiffUtil
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.io.await
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.createVcsChange
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewSupport
import org.jetbrains.plugins.github.pullrequest.comment.action.combined.GHPRCombinedDiffReviewThreadsReloadAction
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContextRepository
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.changes.createReviewSupport
import org.jetbrains.plugins.github.pullrequest.ui.changes.installDiffComputer
import org.jetbrains.plugins.github.pullrequest.ui.review.DelegatingGHPRReviewViewModel
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModel
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModelHelper

@Service(Service.Level.PROJECT)
internal class GHPRCombinedDiffModelProvider(private val project: Project, parentCs: CoroutineScope) {
  private val cs = parentCs.childScope()

  fun createCombinedDiffModel(repository: GHRepositoryCoordinates, pullRequest: GHPRIdentifier): CombinedDiffModelImpl {
    val dataDisposable = Disposer.newDisposable()
    val dataContext = GHPRDataContextRepository.getInstance(project).findContext(repository)!!
    val dataProvider = dataContext.dataProviderRepository.getDataProvider(pullRequest, dataDisposable)

    fun createCombinedDiffModel(repository: GHRepositoryCoordinates, pullRequest: GHPRIdentifier): CombinedDiffModelImpl {
    val model = CombinedDiffModelImpl(project)
    val dataContext = GHPRDataContextRepository.getInstance(project).findContext(repository)!!
    val dataProvider = dataContext.dataProviderRepository.getDataProvider(pullRequest, model.ourDisposable)

    val uiCs = cs.childScope(Dispatchers.Main.immediate + CoroutineName("GitHub Pull Request Review Combined Diff UI"))
    model.ourDisposable.whenDisposed {
      uiCs.cancel("disposed")
    }

    uiCs.launch {
      val reviewVmHelper = GHPRReviewViewModelHelper(this, dataProvider)
      val reviewVm = DelegatingGHPRReviewViewModel(reviewVmHelper)

      var childJob = handleChanges(project, dataContext, dataProvider, reviewVm, model)
      dataProvider.changesData.addChangesListener(model.ourDisposable) {
        childJob.cancel()
        childJob = handleChanges(project, dataContext, dataProvider, reviewVm, model)
      }
    }
    return model
  }

  fun createCombinedDiffModel(repository: GHRepositoryCoordinates): CombinedDiffModelImpl {
    val model = CombinedDiffModelImpl(project)
    val dataContext = GHPRDataContextRepository.getInstance(project).findContext(repository)!!

    val diffModel: GHPRDiffRequestModel = dataContext.newPRDiffModel
    diffModel.addAndInvokeRequestChainListener(model.ourDisposable) {
      model.cleanBlocks()
      diffModel.requestChain?.let {
        val requests = linkedMapOf<CombinedBlockId, DiffRequestProducer>()
        for (request in it.requests) {
          if (request !is ChangeDiffRequestProducer) return@addAndInvokeRequestChainListener
          val change = request.change
          val id = CombinedPathBlockId((change.afterRevision?.file ?: change.beforeRevision?.file)!!, change.fileStatus, null)
          requests[id] = request
        }
        model.setBlocks(requests)
      }
    }
    return model
  }
}

private fun CoroutineScope.handleChanges(
  project: Project,
  dataContext: GHPRDataContext,
  dataProvider: GHPRDataProvider,
  reviewVm: GHPRReviewViewModel,
  model: CombinedDiffModelImpl
) = launch {
  val changesData = dataProvider.changesData
  val result = changesData.loadChanges().await()
  changesData.fetchBaseBranch().await()
  changesData.fetchHeadBranch().await()

  setupReviewActions(model, reviewVm)

  var myChanges: List<RefComparisonChange> = emptyList()

  dataProvider.combinedDiffSelectionModel.changesSelection.collectLatest { changesSelection ->
    if (changesSelection != null) {
      val newChanges = changesSelection.changes
      // fixme: fix after selection rework
      val onlySelectionChangedInTree = newChanges === myChanges
      if (!onlySelectionChangedInTree || model.requests.isEmpty()) {
        myChanges = newChanges
        val list: List<ChangeViewDiffRequestProcessor.ChangeWrapper> = myChanges.map { change ->
          GHPullRequestChangeWrapper(project, dataContext, dataProvider, result, change)
        }

        model.cleanBlocks()
        model.setBlocks(CombinedDiffPreviewModel.prepareCombinedDiffModelRequests(project, list))
      }

      val change = changesSelection.selectedChange ?: return@collectLatest
      val diffViewer = model.context.getUserData(COMBINED_DIFF_VIEWER_KEY) ?: return@collectLatest
      diffViewer.selectDiffBlock(CombinedPathBlockId(change.filePath, change.fileStatus), focusBlock = false)
    }
    else {
      model.cleanBlocks()
    }
  }
}

private fun createData(dataContext: GHPRDataContext,
                       dataProvider: GHPRDataProvider,
                       result: GitBranchComparisonResult,
                       change: RefComparisonChange,
                       project: Project): Map<Key<out Any>, Any?> {
  val requestDataKeys = mutableMapOf<Key<out Any>, Any?>()
  val aFile = change.filePathBefore
  val bFile = change.filePathAfter
  requestDataKeys[DiffUserDataKeysEx.VCS_DIFF_RIGHT_CONTENT_TITLE] =
    VcsDiffUtil.getRevisionTitle(change.revisionNumberAfter.toShortString(), aFile, null)
  requestDataKeys[DiffUserDataKeysEx.VCS_DIFF_LEFT_CONTENT_TITLE] =
    VcsDiffUtil.getRevisionTitle(change.revisionNumberBefore.toShortString(), bFile, aFile)

  installDiffComputer(result, change, requestDataKeys)

  val reviewSupport = createReviewSupport(project, result, change, dataProvider, dataContext)
  if (reviewSupport != null) {
    requestDataKeys[GHPRDiffReviewSupport.KEY] = reviewSupport
    requestDataKeys[DiffUserDataKeys.DATA_PROVIDER] = GenericDataProvider().apply {
      putData(GHPRDiffReviewSupport.DATA_KEY, reviewSupport)
    }
  }

  return requestDataKeys
}

private class GHPullRequestChangeWrapper(
  private val myProject: Project,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider,
  private val result: GitBranchComparisonResult,
  private val refChange: RefComparisonChange,
) : ChangeViewDiffRequestProcessor.ChangeWrapper(refChange.createVcsChange(myProject)) {

  override fun createProducer(project: Project?): DiffRequestProducer? {
    val data = createData(dataContext, dataProvider, result, refChange, myProject)
    return ChangeDiffRequestProducer.create(project, change, data)
  }
}

private fun setupReviewActions(model: CombinedDiffModel, reviewVm: GHPRReviewViewModel) {
  val context = model.context
  DiffUtil.putDataKey(context, GHPRReviewViewModel.DATA_KEY, reviewVm)

  val genericDataProvider = GenericDataProvider().apply {
    putData(GHPRActionKeys.COMBINED_DIFF_PREVIEW_MODEL, model)
  }
  context.putUserData(DiffUserDataKeys.DATA_PROVIDER, genericDataProvider)
  context.putUserData(DiffUserDataKeys.CONTEXT_ACTIONS, listOf(
    ImmutableToolbarLabelAction(CollaborationToolsBundle.message("review.diff.toolbar.label")),
    GHPRCombinedDiffReviewThreadsReloadAction(model),
    ActionManager.getInstance().getAction("Github.PullRequest.Review.Submit")))
}

private fun createReviewSupport(project: Project, result: GitBranchComparisonResult, change: RefComparisonChange,
                                dataProvider: GHPRDataProvider, dataContext: GHPRDataContext): GHPRDiffReviewSupport? {
  return createReviewSupport(project, result, change, dataProvider,
                             dataContext.htmlImageLoader,
                             dataContext.avatarIconsProvider,
                             dataContext.repositoryDataService,
                             dataContext.securityService.ghostUser,
                             dataContext.securityService.currentUser)
}