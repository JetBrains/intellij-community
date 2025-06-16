// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.diff

import com.intellij.collaboration.async.MappingScopedItemsContainer
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.filePath
import com.intellij.diff.util.Range
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.ai.GHPRAICommentViewModel
import org.jetbrains.plugins.github.ai.GHPRAIReviewExtension
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentLocation
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentPosition
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRThreadsViewModels
import org.jetbrains.plugins.github.pullrequest.ui.comment.lineLocation
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewNewCommentEditorViewModel
import org.jetbrains.plugins.github.pullrequest.ui.editor.ranges

interface GHPRDiffReviewViewModel {
  val commentableRanges: List<Range>
  val canComment: Boolean

  val threads: StateFlow<Collection<GHPRReviewThreadDiffViewModel>>
  val newComments: StateFlow<Collection<GHPRNewCommentDiffViewModel>>
  val aiComments: StateFlow<Collection<GHPRAICommentViewModel>>

  val locationsWithDiscussions: StateFlow<Set<DiffLineLocation>>

  fun requestNewComment(location: GHPRReviewCommentLocation, focus: Boolean)
  fun cancelNewComment(location: GHPRReviewCommentLocation)

  fun markViewed()
}

internal class GHPRDiffReviewViewModelImpl(
  project: Project,
  parentCs: CoroutineScope,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider,
  private val change: RefComparisonChange,
  private val diffData: GitTextFilePatchWithHistory,
  private val threadsVms: GHPRThreadsViewModels,
  allThreads: StateFlow<List<MappedGHPRReviewThreadDiffViewModel>>,
) : GHPRDiffReviewViewModel {
  private val cs = parentCs.childScope(javaClass.name)

  override val commentableRanges: List<Range> = diffData.patch.ranges
  override val canComment: Boolean = threadsVms.canComment

  @OptIn(ExperimentalCoroutinesApi::class)
  // Filter out only the threads relevant to the diff
  override val threads: StateFlow<Collection<GHPRReviewThreadDiffViewModel>> =
    allThreads.flatMapLatest { allThreads ->
      combine(allThreads.map { thread ->
        thread.mapping.mapState { mapping -> thread.takeIf { mapping.change == this@GHPRDiffReviewViewModelImpl.change } }
      }) { it.filterNotNull() }
    }.stateInNow(cs, emptyList())

  @OptIn(ExperimentalCoroutinesApi::class)
  override val locationsWithDiscussions: StateFlow<Set<DiffLineLocation>> =
    threads.flatMapLatest { list ->
      combine(list.map { thread ->
        thread.mapping.mapState {
          if (!it.isVisible) return@mapState null
          it.location
        }
      }) { it.filterNotNull().toSet() }
    }.stateInNow(cs, emptySet())

  private val newCommentsContainer =
    MappingScopedItemsContainer.byIdentity<GHPRReviewNewCommentEditorViewModel, GHPRNewCommentDiffViewModelImpl>(cs) {
      GHPRNewCommentDiffViewModelImpl(it.position.location.lineLocation, it)
    }
  override val newComments: StateFlow<Collection<GHPRNewCommentDiffViewModel>> =
    newCommentsContainer.mappingState.mapState { it.values }

  @OptIn(ExperimentalCoroutinesApi::class)
  override val aiComments: StateFlow<Collection<GHPRAICommentViewModel>> =
    GHPRAIReviewExtension.singleFlow
      .flatMapLatest { it?.provideCommentVms(project, dataProvider, change, diffData) ?: flowOf(listOf()) }
      .stateIn(cs, SharingStarted.Eagerly, emptyList())

  init {
    cs.launchNow {
      threadsVms.newComments.collect {
        val commentForChange = it.values.filter { it.position.change == change }
        newCommentsContainer.update(commentForChange)
      }
    }
  }

  override fun requestNewComment(location: GHPRReviewCommentLocation, focus: Boolean) {
    val position = GHPRReviewCommentPosition(change, location)
    val sharedVm = threadsVms.requestNewComment(position)
    if (focus) {
      cs.launchNow {
        newCommentsContainer.addIfAbsent(sharedVm).requestFocus()
      }
    }
  }

  override fun cancelNewComment(location: GHPRReviewCommentLocation) {
    val position = GHPRReviewCommentPosition(change, location)
    threadsVms.cancelNewComment(position)
  }

  override fun markViewed() {
    if (!diffData.isCumulative) return
    cs.launch {
      val repository = dataContext.repositoryDataService.repositoryMapping.gitRepository
      val repositoryRelativePath = VcsFileUtil.relativePath(repository.root, change.filePath)
      // TODO: handle error
      dataProvider.viewedStateData.updateViewedState(listOf(repositoryRelativePath), true)
    }
  }
}
