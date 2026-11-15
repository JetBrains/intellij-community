// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesContainer
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesViewModel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesViewModelDelegate
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.ResultUtil.runCatchingUser
import com.intellij.collaboration.util.filePath
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.GitBranchComparisonResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.mergerequest.GitLabMergeRequestsPreferences
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.ui.GitLabMarkdownToHtmlConverter

@ApiStatus.Internal
interface GitLabMergeRequestChangesViewModel : CodeReviewChangesViewModel<GitLabCommitViewModel> {
  /**
   * View model of a current change list
   */
  val changeListVm: StateFlow<ComputedResult<GitLabMergeRequestChangeListViewModel>>

  fun selectChange(change: RefComparisonChange)
}

private val LOG = logger<GitLabMergeRequestChangesViewModel>()

internal class GitLabMergeRequestChangesViewModelImpl(
  private val project: Project,
  parentCs: CoroutineScope,
  mergeRequest: GitLabMergeRequest,
  private val htmlConverter: GitLabMarkdownToHtmlConverter,
) : GitLabMergeRequestChangesViewModel,
    CodeReviewChangesViewModel<GitLabCommitViewModel> {
  private val cs = parentCs.childScope(javaClass.name)
  private val preferences = project.service<GitLabMergeRequestsPreferences>()

  @OptIn(ExperimentalCoroutinesApi::class)
  private val changesContainer: StateFlow<Result<GitLabChangesContainer>?> =
    combine(mergeRequest.changes, preferences.hideTestSourcesState, ::Pair).mapLatest { (changes, hideTestSources) ->
      runCatchingUser {
        GitLabChangesContainer.create(project, changes.getParsedChanges(), hideTestSources)
      }
    }.stateInNow(cs, null)

  @Volatile
  private var lastChangeListVm: GitLabMergeRequestChangeListViewModelBase? = null

  private val delegate = CodeReviewChangesViewModelDelegate.create(cs, changesContainer.filterNotNull()) { changesContainer, changeList ->
    changesContainer as GitLabChangesContainer

    val parsedChanges = changesContainer.changes
    val testSources = changesContainer.testSourcesOf(changeList.commitSha)
    // a single-commit merge request has no separate cumulative diff, so its only change list is the cumulative one
    val vm = if (changeList.commitSha == null || parsedChanges.commits.size == 1) {
      GitLabMergeRequestCumulativeChangeListViewModelImpl(project, this, mergeRequest, parsedChanges, changeList,
                                                          testSources)
    }
    else {
      GitLabMergeRequestCommitChangeListViewModelImpl(project, this, mergeRequest, parsedChanges, changeList,
                                                      testSources)
    }
    // The diff shows the changes of the selection, so a new change list keeps the selected file or the nearest shown one.
    (lastChangeListVm?.changesSelection?.value as? ChangesSelection.Precise)?.let(vm::selectNearestChange)
    lastChangeListVm = vm
    vm
  }

  override val reviewCommits: SharedFlow<List<GitLabCommitViewModel>> =
    mergeRequest.changes
      .map { it.getCommits().map { commit -> GitLabCommitViewModel(commit, htmlConverter) } }
      .modelFlow(cs, LOG)

  override val selectedCommitIndex: SharedFlow<Int> = reviewCommits.combine(delegate.selectedCommit) { commits, sha ->
    if (sha == null) -1
    else commits.indexOfFirst { it.sha == sha }
  }.modelFlow(cs, LOG)

  override val selectedCommit: SharedFlow<GitLabCommitViewModel?> = reviewCommits.combine(selectedCommitIndex) { commits, index ->
    index.takeIf { it >= 0 }?.let { commits[it] }
  }.modelFlow(cs, LOG)

  override val changeListVm: StateFlow<ComputedResult<GitLabMergeRequestChangeListViewModelBase>> = delegate.changeListVm

  override fun selectCommit(index: Int) {
    delegate.selectCommit(index)?.selectChange(null)
  }

  override fun selectNextCommit() {
    delegate.selectNextCommit()?.selectChange(null)
  }

  override fun selectPreviousCommit() {
    delegate.selectPreviousCommit()?.selectChange(null)
  }

  override fun selectChange(change: RefComparisonChange) {
    val commit = changesContainer.value?.getOrNull()?.let {
      it.commitsByChange[change]
    }
    delegate.selectCommit(commit)?.selectChange(change)
  }

  override fun commitHash(commit: GitLabCommitViewModel): String = commit.shortId
}

/**
 * [summaryChanges] and [changesByCommits] list the changes the change list shows; [changes] keeps every change of the merge request.
 */
internal class GitLabChangesContainer private constructor(
  val changes: GitBranchComparisonResult,
  private val testSources: Set<RefComparisonChange>,
  private val hidesTestSources: Boolean,
  summaryChanges: List<RefComparisonChange>,
  changesByCommits: Map<String, List<RefComparisonChange>>,
) : CodeReviewChangesContainer(
  summaryChanges,
  changes.commits.map { it.sha },
  changesByCommits
) {
  /**
   * Test sources among the changes of [commitSha], or of the whole merge request for `null`
   */
  fun testSourcesOf(commitSha: String?): GitLabTestSources {
    val commitChanges = if (commitSha == null) changes.changes else changes.changesByCommits[commitSha].orEmpty()
    return GitLabTestSources(commitChanges.count(testSources::contains), hidesTestSources)
  }

  companion object {
    suspend fun create(project: Project, changes: GitBranchComparisonResult, hideTestSources: Boolean): GitLabChangesContainer {
      val testSources = findTestSources(project, changes.changes + changes.changesByCommits.values.flatten())
      if (!hideTestSources) {
        return GitLabChangesContainer(changes, testSources, false, changes.changes, changes.changesByCommits)
      }
      return GitLabChangesContainer(changes, testSources, true,
                                    changes.changes.filterNot(testSources::contains),
                                    changes.changesByCommits.mapValues { (_, commitChanges) -> commitChanges.filterNot(testSources::contains) })
    }
  }
}

/**
 * Returns the changes whose file lies in test source content of [project].
 * A file missing locally, such as a deleted or not yet fetched one, is judged by its nearest existing parent directory.
 */
private suspend fun findTestSources(project: Project, changes: Collection<RefComparisonChange>): Set<RefComparisonChange> = readAction {
  val fileIndex = ProjectFileIndex.getInstance(project)
  changes.filterTo(HashSet()) { change ->
    change.filePath.findNearestExistingFile()?.let(fileIndex::isInTestSourceContent) ?: false
  }
}

private fun FilePath.findNearestExistingFile(): VirtualFile? =
  generateSequence(this) { it.parentPath }.firstNotNullOfOrNull { it.virtualFile }
