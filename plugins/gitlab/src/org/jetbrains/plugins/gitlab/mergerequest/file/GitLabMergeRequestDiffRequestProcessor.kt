// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.gitlab.mergerequest.file

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.MutableDiffRequestChainProcessor
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.requests.ErrorDiffRequest
import com.intellij.diff.requests.LoadingDiffRequest
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.diff.impl.GenericDataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.history.VcsDiffUtil
import com.intellij.util.cancelOnDispose
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.getDiffComputer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabLazyProject
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestChanges
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.mergerequest.diff.*

fun createMergeRequestDiffRequestProcessor(project: Project,
                                           currentUser: GitLabUserDTO,
                                           projectData: GitLabLazyProject,
                                           diffBridge: GitLabMergeRequestDiffBridge,
                                           avatarIconsProvider: IconsProvider<GitLabUserDTO>,
                                           mergeRequestId: GitLabMergeRequestId): DiffRequestProcessor {
  val job = SupervisorJob()
  val uiCs = CoroutineScope(job + Dispatchers.Main.immediate + CoroutineName("GitLab Merge Request Review Diff UI"))
  val processor = MutableDiffRequestChainProcessor(project, SimpleDiffRequestChain(LoadingDiffRequest()))
  job.cancelOnDispose(processor)

  uiCs.launchNow {
    projectData.mergeRequests.getShared(mergeRequestId)
      .mapNotNull(Result<GitLabMergeRequest>::getOrNull)
      .collectLatest { mr ->
        coroutineScope {
          val vm = GitLabMergeRequestDiffReviewViewModelImpl(this, currentUser, mr, avatarIconsProvider)
          processor.putContextUserData(GitLabMergeRequestDiffReviewViewModel.KEY, vm)

          launchNow {
            diffBridge.displayedChanges.mapToDiffChain(project, vm, mr.changes).collectLatest {
              processor.chain = it
            }
          }
          awaitCancellation()
        }
      }
  }

  uiCs.launchNow {
    callbackFlow {
      val listener = MutableDiffRequestChainProcessor.SelectionListener { producer ->
        trySend((producer as? ChangeDiffRequestProducer)?.change)
      }
      processor.selectionEventDispatcher.addListener(listener)
      awaitClose {
        processor.selectionEventDispatcher.removeListener(listener)
      }
    }.collect {
      diffBridge.changeSelected(it)
    }
  }

  return processor
}

private fun Flow<ChangesSelection>.mapToDiffChain(
  project: Project,
  reviewVm: GitLabMergeRequestDiffReviewViewModel,
  changesFlow: Flow<GitLabMergeRequestChanges>
): Flow<DiffRequestChain?> =
  combineTransformLatest(this, changesFlow) { selection, changes ->
    if (selection.changes.isEmpty()) {
      emit(null)
      return@combineTransformLatest
    }

    val changesBundle: GitBranchComparisonResult = try {
      loadRevisionsAndParseChanges(changes)
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (e: Exception) {
      emit(SimpleDiffRequestChain(ErrorDiffRequest(e)))
      return@combineTransformLatest
    }


    val producers = selection.toProducersSelection { change, location ->
      val changeDataKeys = createData(reviewVm, changesBundle, change, location)
      ChangeDiffRequestProducer.create(project, change, changeDataKeys)
    }

    emit(producers.let(SimpleDiffRequestChain::fromProducers))
  }

private suspend fun loadRevisionsAndParseChanges(changes: GitLabMergeRequestChanges): GitBranchComparisonResult =
  coroutineScope {
    launch {
      changes.ensureAllRevisionsFetched()
    }
    changes.getParsedChanges()
  }

private fun createData(
  reviewVm: GitLabMergeRequestDiffReviewViewModel,
  parsedChanges: GitBranchComparisonResult,
  change: Change,
  location: DiffLineLocation?
): Map<Key<out Any>, Any?> {
  val requestDataKeys = mutableMapOf<Key<out Any>, Any?>()

  VcsDiffUtil.putFilePathsIntoChangeContext(change, requestDataKeys)
  val dataProvider = GenericDataProvider()
  requestDataKeys[DiffUserDataKeys.DATA_PROVIDER] = dataProvider
  dataProvider.putData(GitLabMergeRequestDiffReviewViewModel.DATA_KEY, reviewVm)
  requestDataKeys[DiffUserDataKeys.CONTEXT_ACTIONS] =
    listOf(ActionManager.getInstance().getAction("GitLab.MergeRequest.Review.Submit"))

  val diffComputer = parsedChanges.patchesByChange[change]?.getDiffComputer()
  if (diffComputer != null) {
    requestDataKeys[DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER] = diffComputer
  }

  if (location != null) {
    requestDataKeys[DiffUserDataKeys.SCROLL_TO_LINE] = Pair(location.first, location.second)
  }

  return requestDataKeys
}

@OptIn(ExperimentalCoroutinesApi::class)
private inline fun <reified T1, reified T2, R> combineTransformLatest(
  flow1: Flow<T1>,
  flow2: Flow<T2>,
  noinline transform: suspend FlowCollector<R>.(T1, T2) -> Unit
): Flow<R> =
  combine(flow1, flow2) { v1, v2 -> v1 to v2 }
    .transformLatest { (v1, v2) ->
      transform(v1, v2)
    }

private fun ChangesSelection.toProducersSelection(mapper: (Change, DiffLineLocation?) -> DiffRequestProducer?)
  : ListSelection<out DiffRequestProducer> = when (this) {
  is ChangesSelection.Multiple -> ListSelection.createAt(changes.mapNotNull { mapper(it, null) }, 0).asExplicitSelection()
  is ChangesSelection.Single -> {
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