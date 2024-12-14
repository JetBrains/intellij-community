// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.editor

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.editor.CodeReviewInEditorViewModel
import com.intellij.collaboration.util.*
import com.intellij.diff.util.Side
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.actions.VcsContextFactory
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.changesComputationState
import org.jetbrains.plugins.github.pullrequest.ui.GHPRReviewBranchStateSharedViewModel
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRThreadsViewModels

interface GHPRReviewInEditorViewModel : CodeReviewInEditorViewModel {
  fun getViewModelFor(file: VirtualFile): Flow<GHPRReviewFileEditorViewModel?>
}

private val LOG = logger<GHPRReviewInEditorViewModelImpl>()

internal class GHPRReviewInEditorViewModelImpl(
  private val project: Project,
  parentCs: CoroutineScope,
  private val settings: GithubPullRequestsProjectUISettings,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider,
  private val sharedBranchVm: GHPRReviewBranchStateSharedViewModel,
  private val threadsVms: GHPRThreadsViewModels,
  private val showDiff: (ChangesSelection) -> Unit
) : GHPRReviewInEditorViewModel {
  private val cs = parentCs.childScope(javaClass.name)
  private val repository = dataContext.repositoryDataService.repositoryMapping.gitRepository

  private val changesComputationState =
    dataProvider.changesData.changesComputationState().onEach {
      it.onFailure {
        LOG.warn("Couldn't load changes for PR ${dataProvider.id.number}", it)
      }
    }.stateIn(cs, SharingStarted.Lazily, ComputedResult.loading())

  private val filesVmsMap = mutableMapOf<FilePath, StateFlow<GHPRReviewFileEditorViewModelImpl?>>()

  private val _discussionsViewOption: MutableStateFlow<DiscussionsViewOption> = MutableStateFlow(DiscussionsViewOption.UNRESOLVED_ONLY)
  override val discussionsViewOption: StateFlow<DiscussionsViewOption> =
    settings.editorReviewEnabledState.combineState(_discussionsViewOption) { enabled, viewOption ->
      if (!enabled) DiscussionsViewOption.DONT_SHOW else viewOption
    }

  override val updateRequired: StateFlow<Boolean> = sharedBranchVm.updateRequired

  override fun updateBranch() = sharedBranchVm.updateBranch()

  /**
   * A view model for [file] review
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  override fun getViewModelFor(file: VirtualFile): Flow<GHPRReviewFileEditorViewModelImpl?> {
    if (!file.isValid || file.isDirectory || !VfsUtilCore.isAncestor(repository.root, file, true)) {
      return flowOf(null)
    }
    val filePath = VcsContextFactory.getInstance().createFilePathOn(file)

    return filesVmsMap.getOrPut(filePath) {
      changesComputationState.mapNotNull {
        it.getOrNull()
      }.distinctUntilChangedBy {
        it.baseSha + it.headSha + it.mergeBaseSha
      }.transformLatest { parsedChanges ->
        val change = parsedChanges.changes.find { it.filePathAfter == filePath }
        if (change == null) {
          emit(null)
          return@transformLatest
        }
        val diffData = parsedChanges.patchesByChange[change] ?: run {
          LOG.info("Diff data not found for change $change")
          emit(null)
          return@transformLatest
        }
        coroutineScope {
          val vm = createFileVm(change, diffData)
          emit(vm)
          awaitCancellation()
        }
      }.stateIn(cs, SharingStarted.WhileSubscribed(0, 0), null)
    }
  }

  override fun setDiscussionsViewOption(viewOption: DiscussionsViewOption) {
    if (viewOption == DiscussionsViewOption.DONT_SHOW) {
      settings.editorReviewEnabled = false
    }
    else {
      settings.editorReviewEnabled = true
      _discussionsViewOption.value = viewOption
    }
  }

  private fun showDiff(changeToShow: RefComparisonChange, lineIdx: Int?) {
    val changesBundle = changesComputationState.value.getOrNull() ?: return
    val selection = changesBundle.createSelection(changeToShow, lineIdx) ?: return
    showDiff(selection)
  }

  private fun CoroutineScope.createFileVm(change: RefComparisonChange, diffData: GitTextFilePatchWithHistory)
    : GHPRReviewFileEditorViewModelImpl =
    GHPRReviewFileEditorViewModelImpl(project, this, dataContext, dataProvider, change, diffData, threadsVms, discussionsViewOption, ::showDiff)
}

private fun GitBranchComparisonResult.createSelection(change: RefComparisonChange, lineIdx: Int?): ChangesSelection? {
  val location = lineIdx?.let { line -> Side.RIGHT to line }
  return if (changes.contains(change)) {
    ChangesSelection.Precise(changes, change, location)
  }
  else {
    changesByCommits[change.revisionNumberAfter.asString()]?.let {
      ChangesSelection.Precise(it, change, location)
    }
  }
}
