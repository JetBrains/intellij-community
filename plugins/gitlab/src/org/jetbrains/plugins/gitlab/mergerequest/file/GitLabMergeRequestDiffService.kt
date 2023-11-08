// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.file

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.util.*
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.requests.ErrorDiffRequest
import com.intellij.diff.requests.LoadingDiffRequest
import com.intellij.diff.tools.combined.COMBINED_DIFF_VIEWER_KEY
import com.intellij.diff.tools.combined.CombinedDiffModelImpl
import com.intellij.diff.tools.combined.CombinedPathBlockId
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.changes.actions.diff.CombinedDiffPreviewModel
import com.intellij.openapi.vcs.changes.ui.MutableDiffRequestChainProcessor
import com.intellij.openapi.vcs.history.VcsDiffUtil
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.cancelOnDispose
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.createVcsChange
import git4idea.changes.getDiffComputer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestChanges
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model.GitLabToolWindowProjectViewModel

@Service(Service.Level.PROJECT)
internal class GitLabMergeRequestDiffService(private val project: Project, parentCs: CoroutineScope) {
  private val cs = parentCs.childScope(Dispatchers.Main.immediate)

  fun createDiffRequestProcessor(projectVm: GitLabToolWindowProjectViewModel, mergeRequestIid: String): DiffRequestProcessor {
    val processor = MutableDiffRequestChainProcessor(project, SimpleDiffRequestChain(LoadingDiffRequest()))
    cs.launchNow(CoroutineName("GitLab Merge Request Review Diff UI")) {
      projectVm.getDiffViewModel(mergeRequestIid).collectLatest {
        val diffVm = it.getOrNull() ?: return@collectLatest
        processor.putContextUserData(GitLabMergeRequestDiffViewModel.KEY, diffVm)

        launch {
          diffVm.submittableReview.collectLatest {
            processor.toolbar.updateActionsAsync()
          }
        }

        try {
          handleChanges(diffVm, processor)
          awaitCancellation()
        }
        catch (e: Exception) {
          processor.chain = null
          processor.putContextUserData(GitLabMergeRequestDiffViewModel.KEY, null)
        }
      }
    }.cancelOnDispose(processor)
    return processor
  }

  private suspend fun handleChanges(diffVm: GitLabMergeRequestDiffViewModel, processor: MutableDiffRequestChainProcessor) {
    var currentSelection: ChangesSelection? = null
    val selectionListener = MutableDiffRequestChainProcessor.SelectionListener { producer ->
      val change = (producer as? ChangeDiffRequestProducer)?.changeContext?.get(RefComparisonChange.KEY) as? RefComparisonChange
      if (currentSelection != null && change != null) {
        val newSelection = currentSelection!!.copyWithSelection(change)
        currentSelection = newSelection
        diffVm.showChanges(newSelection)
      }
    }
    diffVm.changes.collectLatest { changes ->
      diffVm.changesToShow.collect { selection ->
        val needUpdate =
          if (selection is ChangesSelection.Precise && selection.location != null) {
            true
          }
          else {
            !currentSelection.equalChanges(selection)
          }

        if (needUpdate) {
          processor.selectionEventDispatcher.removeListener(selectionListener)

          if (selection.changes.isEmpty()) {
            processor.chain = null
            return@collect
          }

          val changesBundle: GitBranchComparisonResult = try {
            loadRevisionsAndParseChanges(changes)
          }
          catch (ce: CancellationException) {
            throw ce
          }
          catch (e: Exception) {
            processor.chain = SimpleDiffRequestChain(ErrorDiffRequest(e))
            return@collect
          }

          val producers = selection.toProducersSelection { change, location ->
            val changeDataKeys = createData(changesBundle, change, location)
            ChangeDiffRequestProducer.create(project, change.createVcsChange(project), changeDataKeys)
          }
          processor.chain = producers.let(SimpleDiffRequestChain::fromProducers)
          currentSelection = selection
          processor.selectionEventDispatcher.addListener(selectionListener)
        }
      }
    }
  }

  fun createGitLabCombinedDiffModel(connectionId: String, mergeRequestIid: String): CombinedDiffModelImpl {
    val model = CombinedDiffModelImpl(project, null).apply {
      context.putUserData(DiffUserDataKeys.CONTEXT_ACTIONS,
                          listOf(ActionManager.getInstance().getAction("GitLab.MergeRequest.Review.Submit")))
    }

    val projectVm = findProjectVm(project, connectionId)
    if (projectVm != null) {
      cs.launchNow(CoroutineName("GitLab Merge Request Review Combined Diff UI")) {
        projectVm.getDiffViewModel(mergeRequestIid).collectLatest {
          val diffVm = it.getOrNull() ?: return@collectLatest
          model.context.putUserData(GitLabMergeRequestDiffViewModel.KEY, diffVm)
          try {
            handleChanges(diffVm, model)
            awaitCancellation()
          }
          catch (e: Exception) {
            model.cleanBlocks()
            model.context.putUserData(GitLabMergeRequestDiffViewModel.KEY, null)
          }
        }
      }.cancelOnDispose(model.ourDisposable)
    }
    return model
  }

  private suspend fun handleChanges(diffVm: GitLabMergeRequestDiffViewModel, model: CombinedDiffModelImpl) {
    diffVm.changes.collectLatest { changes ->
      var myChanges: List<RefComparisonChange> = emptyList()
      val branchComparisonResult = loadRevisionsAndParseChanges(changes)

      diffVm.changesToShow.collectLatest { changeSelection ->
        val newChanges = changeSelection.changes

        // fixme: fix after selection rework
        val onlySelectionChangedInTree = newChanges === myChanges

        if (!onlySelectionChangedInTree || model.requests.isEmpty()) {

          myChanges = newChanges
          val list: List<ChangeViewDiffRequestProcessor.ChangeWrapper> = myChanges.map { change ->
            GitLabMergeRequestChangeWrapper(project, change, branchComparisonResult)
          }

          model.cleanBlocks()
          model.setBlocks(CombinedDiffPreviewModel.prepareCombinedDiffModelRequests(project, list))
        }

        val change = changeSelection.selectedChange ?: return@collectLatest
        val diffViewer = model.context.getUserData(COMBINED_DIFF_VIEWER_KEY) ?: return@collectLatest
        diffViewer.selectDiffBlock(CombinedPathBlockId(change.filePath, change.fileStatus), focusBlock = false)
      }
    }
  }
}

private suspend fun loadRevisionsAndParseChanges(changes: GitLabMergeRequestChanges): GitBranchComparisonResult =
  coroutineScope {
    launch {
      changes.ensureAllRevisionsFetched()
    }
    changes.getParsedChanges()
  }

private fun createData(
  parsedChanges: GitBranchComparisonResult,
  change: RefComparisonChange,
  location: DiffLineLocation?
): Map<Key<out Any>, Any?> {
  val requestDataKeys = mutableMapOf<Key<out Any>, Any?>()

  requestDataKeys[RefComparisonChange.KEY] = change

  val aFile = change.filePathBefore
  val bFile = change.filePathAfter
  requestDataKeys[DiffUserDataKeysEx.VCS_DIFF_RIGHT_CONTENT_TITLE] =
    VcsDiffUtil.getRevisionTitle(change.revisionNumberAfter.toShortString(), aFile, null)
  requestDataKeys[DiffUserDataKeysEx.VCS_DIFF_LEFT_CONTENT_TITLE] =
    VcsDiffUtil.getRevisionTitle(change.revisionNumberBefore.toShortString(), bFile, aFile)

  val diffComputer = parsedChanges.patchesByChange[change]?.getDiffComputer()
  if (diffComputer != null) {
    requestDataKeys[DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER] = diffComputer
  }

  if (location != null) {
    requestDataKeys[DiffUserDataKeys.SCROLL_TO_LINE] = Pair(location.first, location.second)
  }

  return requestDataKeys
}

private fun ChangesSelection.toProducersSelection(mapper: (RefComparisonChange, DiffLineLocation?) -> DiffRequestProducer?)
  : ListSelection<out DiffRequestProducer> = when (this) {
  is ChangesSelection.Fuzzy -> ListSelection.createAt(changes.mapNotNull { mapper(it, null) }, 0).asExplicitSelection()
  is ChangesSelection.Precise -> {
    var newSelectionIndex = -1
    val result = mutableListOf<DiffRequestProducer>()
    for (i in changes.indices) {
      if (i == selectedIdx) newSelectionIndex = result.size
      val out = mapper(changes[i], location?.takeIf { i == selectedIdx })
      if (out != null) result.add(out)
    }
    ListSelection.createAt(result, newSelectionIndex)
  }
}

private class GitLabMergeRequestChangeWrapper(
  project: Project,
  private val refChange: RefComparisonChange,
  private val result: GitBranchComparisonResult
) : ChangeViewDiffRequestProcessor.ChangeWrapper(refChange.createVcsChange(project)) {

  override fun createProducer(project: Project?): DiffRequestProducer? {
    val data = createData(result, refChange, null)
    return ChangeDiffRequestProducer.create(project, change, data)
  }
}