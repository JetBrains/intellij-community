// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.action.ImmutableToolbarLabelAction
import com.intellij.collaboration.util.selectedChange
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.tools.combined.COMBINED_DIFF_VIEWER_KEY
import com.intellij.diff.tools.combined.CombinedDiffModel
import com.intellij.diff.tools.combined.CombinedDiffModelImpl
import com.intellij.diff.tools.combined.CombinedPathBlockId
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diff.impl.GenericDataProvider
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.changes.actions.diff.CombinedDiffPreviewModel
import com.intellij.openapi.vcs.history.VcsDiffUtil
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.io.await
import git4idea.changes.GitBranchComparisonResult
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
import org.jetbrains.plugins.github.pullrequest.ui.changes.installReviewSupport

@Service(Service.Level.PROJECT)
internal class GHPRCreateCombinedDiffModelProvider(private val project: Project, parentCs: CoroutineScope) {
  private val cs = parentCs.childScope()

  fun createCombinedDiffModel(repository: GHRepositoryCoordinates, pullRequest: GHPRIdentifier): CombinedDiffModelImpl {
    val dataDisposable = Disposer.newDisposable()
    val dataContext = GHPRDataContextRepository.getInstance(project).findContext(repository)!!
    val dataProvider = dataContext.dataProviderRepository.getDataProvider(pullRequest, dataDisposable)

    val model = CombinedDiffModelImpl(project)

    val uiCs = cs.childScope(Dispatchers.Main.immediate + CoroutineName("GitLab Merge Request Review Combined Diff UI"))
    model.ourDisposable.whenDisposed {
      uiCs.cancel("disposed")
    }

    var childJob = uiCs.handleChanges(dataProvider, model, dataContext, project)
    dataProvider.changesData.addChangesListener(model.ourDisposable) {
      childJob.cancel()
      childJob = uiCs.handleChanges(dataProvider, model, dataContext, project)
    }

    return model
  }
}

private fun CoroutineScope.handleChanges(
  dataProvider: GHPRDataProvider,
  model: CombinedDiffModelImpl,
  dataContext: GHPRDataContext,
  project: Project
) = launch {
  val changesData = dataProvider.changesData
  val result = changesData.loadChanges().await()
  changesData.fetchBaseBranch().await()
  changesData.fetchHeadBranch().await()

  setupReviewActions(model, dataProvider)

  var myChanges: List<Change> = emptyList()

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
      diffViewer.selectDiffBlock(CombinedPathBlockId(ChangesUtil.getFilePath(change), change.fileStatus), focusBlock = false)
    }
    else {
      model.cleanBlocks()
    }
  }
}

private fun createData(dataContext: GHPRDataContext,
                       dataProvider: GHPRDataProvider,
                       result: GitBranchComparisonResult,
                       change: Change,
                       project: Project): Map<Key<out Any>, Any?> {
  val requestDataKeys = mutableMapOf<Key<out Any>, Any?>()
  VcsDiffUtil.putFilePathsIntoChangeContext(change, requestDataKeys)

  installDiffComputer(result, change, requestDataKeys)

  val reviewSupport = createReviewSupport(project, result, change, dataProvider, dataContext)
  if (reviewSupport != null) {
    installReviewSupport(requestDataKeys, reviewSupport, dataProvider)
  }

  return requestDataKeys
}

private class GHPullRequestChangeWrapper(
  private val myProject: Project,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider,
  private val result: GitBranchComparisonResult,
  change: Change,
) : ChangeViewDiffRequestProcessor.ChangeWrapper(change) {

  override fun createProducer(project: Project?): DiffRequestProducer? {
    val data = createData(dataContext, dataProvider, result, change, myProject)
    return ChangeDiffRequestProducer.create(project, change, data)
  }
}

private fun setupReviewActions(model: CombinedDiffModel, dataProvider: GHPRDataProvider) {
  val context = model.context
  DiffUtil.putDataKey(context, GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER, dataProvider)

  val genericDataProvider = GenericDataProvider().apply {
    putData(GHPRActionKeys.COMBINED_DIFF_PREVIEW_MODEL, model)
  }
  context.putUserData(DiffUserDataKeys.DATA_PROVIDER, genericDataProvider)
  context.putUserData(DiffUserDataKeys.CONTEXT_ACTIONS, listOf(
    ImmutableToolbarLabelAction(CollaborationToolsBundle.message("review.diff.toolbar.label")),
    GHPRCombinedDiffReviewThreadsReloadAction(model),
    ActionManager.getInstance().getAction("Github.PullRequest.Review.Submit")))
}

private fun createReviewSupport(project: Project, result: GitBranchComparisonResult, change: Change,
                                dataProvider: GHPRDataProvider, dataContext: GHPRDataContext): GHPRDiffReviewSupport? {
  return createReviewSupport(project, result, change, dataProvider,
                             dataContext.htmlImageLoader,
                             dataContext.avatarIconsProvider,
                             dataContext.repositoryDataService,
                             dataContext.securityService.ghostUser,
                             dataContext.securityService.currentUser)
}