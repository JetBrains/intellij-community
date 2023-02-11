// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff

import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.requests.ErrorDiffRequest
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.ListSelection
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.history.VcsDiffUtil
import git4idea.changes.GitParsedChangesBundle
import git4idea.changes.getDiffComputer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabLazyProject
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestChanges
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId

interface GitLabMergeRequestDiffBridge {
  val chain: Flow<DiffRequestChain?>

  fun setChanges(changes: ListSelection<Change>)
  fun selectFilePath(filePath: FilePath)
}

@OptIn(ExperimentalCoroutinesApi::class)
class GitLabMergeRequestDiffBridgeImpl(private val project: Project,
                                       projectData: GitLabLazyProject,
                                       mrId: GitLabMergeRequestId) : GitLabMergeRequestDiffBridge {

  private val selectionState = MutableStateFlow<ListSelection<Change>?>(null)

  override val chain: Flow<DiffRequestChain?> = projectData.mergeRequests.getShared(mrId).flatMapLatest { mrRes ->
    mrRes.fold(
      onSuccess = { selectionState.mapToDiffChain(it.changes) },
      onFailure = { flowOf(SimpleDiffRequestChain(ErrorDiffRequest(it))) }
    )
  }

  override fun setChanges(changes: ListSelection<Change>) {
    selectionState.value = changes
  }

  override fun selectFilePath(filePath: FilePath) {
    // TODO: implement
  }

  private fun Flow<ListSelection<Change>?>.mapToDiffChain(changesFlow: Flow<GitLabMergeRequestChanges>): Flow<DiffRequestChain?> =
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



