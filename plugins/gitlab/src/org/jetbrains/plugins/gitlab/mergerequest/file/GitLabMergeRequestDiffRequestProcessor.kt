// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.gitlab.mergerequest.file

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.MutableDiffRequestChainProcessor
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.requests.ErrorDiffRequest
import com.intellij.diff.requests.LoadingDiffRequest
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.ListSelection
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
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestChanges
import com.intellij.collaboration.util.ChangesSelection
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffViewModel
import com.intellij.collaboration.util.equalChanges
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabToolWindowProjectViewModel

internal fun createMergeRequestDiffRequestProcessor(project: Project,
                                                    projectVm: GitLabToolWindowProjectViewModel,
                                                    mergeRequestIid: String): DiffRequestProcessor {
  val job = SupervisorJob()
  val uiCs = CoroutineScope(job + Dispatchers.Main.immediate + CoroutineName("GitLab Merge Request Review Diff UI"))
  val processor = MutableDiffRequestChainProcessor(project, SimpleDiffRequestChain(LoadingDiffRequest()))
  job.cancelOnDispose(processor)

  uiCs.launchNow {
    projectVm.getDiffViewModel(mergeRequestIid).collectLatest {
      val diffVm = it.getOrNull() ?: return@collectLatest

      processor.putContextUserData(GitLabMergeRequestDiffViewModel.KEY, diffVm)

      handleChanges(project, diffVm, processor)

      try {
        awaitCancellation()
      }
      catch (e: Exception) {
        processor.chain = null
        processor.putContextUserData(GitLabMergeRequestDiffViewModel.KEY, null)
      }
    }
  }

  return processor
}

private suspend fun handleChanges(project: Project,
                                  diffVm: GitLabMergeRequestDiffViewModel,
                                  processor: MutableDiffRequestChainProcessor) {
  var currentSelection: ChangesSelection? = null
  val selectionListener = MutableDiffRequestChainProcessor.SelectionListener { producer ->
    val change = (producer as? ChangeDiffRequestProducer)?.change
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
          ChangeDiffRequestProducer.create(project, change, changeDataKeys)
        }
        processor.chain = producers.let(SimpleDiffRequestChain::fromProducers)
        currentSelection = selection
        processor.selectionEventDispatcher.addListener(selectionListener)
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
  change: Change,
  location: DiffLineLocation?
): Map<Key<out Any>, Any?> {
  val requestDataKeys = mutableMapOf<Key<out Any>, Any?>()

  VcsDiffUtil.putFilePathsIntoChangeContext(change, requestDataKeys)

  val diffComputer = parsedChanges.patchesByChange[change]?.getDiffComputer()
  if (diffComputer != null) {
    requestDataKeys[DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER] = diffComputer
  }

  if (location != null) {
    requestDataKeys[DiffUserDataKeys.SCROLL_TO_LINE] = Pair(location.first, location.second)
  }

  return requestDataKeys
}

private fun ChangesSelection.toProducersSelection(mapper: (Change, DiffLineLocation?) -> DiffRequestProducer?)
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