// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.gitlab.mergerequest.file

import com.intellij.collaboration.ui.codereview.diff.MutableDiffRequestChainProcessor
import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.requests.ErrorDiffRequest
import com.intellij.diff.requests.LoadingDiffRequest
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.ListSelection
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.history.VcsDiffUtil
import com.intellij.util.cancelOnDispose
import com.intellij.util.childScope
import git4idea.changes.GitParsedChangesBundle
import git4idea.changes.getDiffComputer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestChanges
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffBridge
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffReviewViewModel
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffReviewViewModelImpl

@OptIn(ExperimentalCoroutinesApi::class)
fun createMergeRequestDiffRequestProcessor(project: Project,
                                           connection: GitLabProjectConnection,
                                           diffBridge: GitLabMergeRequestDiffBridge,
                                           mergeRequestId: GitLabMergeRequestId): DiffRequestProcessor {
  val job = SupervisorJob()
  val cs = CoroutineScope(job)

  val reviewVm = GitLabMergeRequestDiffReviewViewModelImpl(cs, connection.currentUser, connection.projectData, mergeRequestId)

  val uiCs = cs.childScope(Dispatchers.Main.immediate)
  val processor = MutableDiffRequestChainProcessor(project, SimpleDiffRequestChain(LoadingDiffRequest())).apply {
    putContextUserData(GitLabProjectConnection.KEY, connection)
    putContextUserData(GitLabMergeRequestDiffReviewViewModel.KEY, reviewVm)
  }

  job.cancelOnDispose(processor)

  uiCs.launch(start = CoroutineStart.UNDISPATCHED) {
    connection.projectData.mergeRequests.getShared(mergeRequestId).flatMapLatest { res ->
      res.fold(
        onSuccess = { diffBridge.changes.mapToDiffChain(project, it.changes) },
        onFailure = { flowOf(SimpleDiffRequestChain(ErrorDiffRequest(it))) }
      )
    }.collectLatest {
      processor.chain = it
    }
  }

  uiCs.launch(start = CoroutineStart.UNDISPATCHED) {
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

private fun Flow<ListSelection<Change>?>.mapToDiffChain(project: Project, changesFlow: Flow<GitLabMergeRequestChanges>)
  : Flow<DiffRequestChain?> =
  combineTransformLatest(this, changesFlow) { selection, changes ->
    if (selection == null || selection.isEmpty) {
      emit(null)
      return@combineTransformLatest
    }

    val changesBundle: GitParsedChangesBundle = try {
      loadRevisionsAndParseChanges(changes)
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (e: Exception) {
      emit(SimpleDiffRequestChain(ErrorDiffRequest(e)))
      return@combineTransformLatest
    }

    val producers = selection.map {
      val changeDataKeys = createData(changesBundle, it)
      ChangeDiffRequestProducer.create(project, it, changeDataKeys)
    }

    emit(producers.let(SimpleDiffRequestChain::fromProducers))
  }

private suspend fun loadRevisionsAndParseChanges(changes: GitLabMergeRequestChanges): GitParsedChangesBundle =
  coroutineScope {
    launch {
      changes.ensureAllRevisionsFetched()
    }
    changes.getParsedChanges()
  }

private fun createData(
  parsedChanges: GitParsedChangesBundle,
  change: Change
): Map<Key<out Any>, Any?> {
  val requestDataKeys = mutableMapOf<Key<out Any>, Any?>()

  VcsDiffUtil.putFilePathsIntoChangeContext(change, requestDataKeys)

  val diffComputer = parsedChanges.patchesByChange[change]?.getDiffComputer()
  if (diffComputer != null) {
    requestDataKeys[DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER] = diffComputer
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