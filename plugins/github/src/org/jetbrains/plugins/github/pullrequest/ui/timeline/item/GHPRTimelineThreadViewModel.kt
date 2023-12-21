// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline.item

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapDataToModel
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.codereview.comment.CodeReviewSubmittableTextViewModelBase
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.timeline.CollapsibleTimelineItemViewModel
import com.intellij.collaboration.ui.codereview.timeline.TimelineDiffComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.thread.CodeReviewFoldableThreadViewModel
import com.intellij.collaboration.ui.codereview.timeline.thread.CodeReviewResolvableItemViewModel
import com.intellij.collaboration.ui.html.AsyncHtmlImageLoader
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.diff.util.LineRange
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.openapi.diff.impl.patch.PatchHunkUtil
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.actions.VcsContextFactory
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.GitBranchComparisonResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewCommentState
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRNewThreadCommentViewModel
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import java.util.*

interface GHPRTimelineThreadViewModel
  : CollapsibleTimelineItemViewModel,
    CodeReviewFoldableThreadViewModel,
    CodeReviewResolvableItemViewModel {
  val project: Project
  val avatarIconsProvider: GHAvatarIconsProvider
  val htmlImageLoader: AsyncHtmlImageLoader

  val id: String
  val author: GHActor
  val createdAt: Date?

  val isOutdated: StateFlow<Boolean>
  val isPending: StateFlow<Boolean>

  val filePath: String
  val patchHunkWithAnchor: StateFlow<Pair<PatchHunk, LineRange?>>

  val mainCommentVm: StateFlow<GHPRTimelineThreadCommentViewModel?>
  val replies: StateFlow<List<GHPRTimelineThreadCommentViewModel>>

  val newReplyVm: GHPRNewThreadCommentViewModel

  /**
   * Show diff for a file this thread belongs to
   */
  fun showDiff()
}

private val LOG = logger<UpdateableGHPRTimelineThreadViewModel>()

class UpdateableGHPRTimelineThreadViewModel internal constructor(
  override val project: Project,
  parentCs: CoroutineScope,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider,
  initialData: GHPullRequestReviewThread
) : GHPRTimelineThreadViewModel {
  private val cs = parentCs.childScope(CoroutineName("GitHub Pull Request Timeline Thread View Model"))
  private val reviewData: GHPRReviewDataProvider = dataProvider.reviewData
  private val taskLauncher = SingleCoroutineLauncher(cs)

  override val avatarIconsProvider: GHAvatarIconsProvider = dataContext.avatarIconsProvider
  override val htmlImageLoader: AsyncHtmlImageLoader = dataContext.htmlImageLoader

  private val dataState = MutableStateFlow(initialData)

  override val isBusy: StateFlow<Boolean> = taskLauncher.busy

  override val id = initialData.id
  override val author: GHActor = initialData.author ?: dataContext.securityService.ghostUser
  override val createdAt: Date = initialData.createdAt
  override val filePath: String = initialData.path

  override val isOutdated: StateFlow<Boolean> = dataState.mapState { it.isOutdated }
  override val isPending: StateFlow<Boolean> = dataState.mapState {
    it.comments.firstOrNull()?.state == GHPullRequestReviewCommentState.PENDING
  }

  override val patchHunkWithAnchor: StateFlow<Pair<PatchHunk, LineRange?>> = dataState.mapState {
    calcDiffWithAnchor(it)
  }

  private val commentsVms = dataState
    .map { it.comments }
    .mapDataToModel({ it.id }, { createComment(it) }, { update(it) })
    .stateIn(cs, SharingStarted.Eagerly, emptyList())

  override val mainCommentVm: StateFlow<GHPRTimelineThreadCommentViewModel> = commentsVms.mapState { it.first() }
  override val replies: StateFlow<List<GHPRTimelineThreadCommentViewModel>> = commentsVms.mapState { it.drop(1) }

  override val canChangeResolvedState: StateFlow<Boolean> =
    dataState.mapState { it.viewerCanResolve || it.viewerCanUnresolve }
  override val isResolved: StateFlow<Boolean> = dataState.mapState { it.isResolved }

  override val repliesState: StateFlow<CodeReviewFoldableThreadViewModel.RepliesStateData> = dataState.mapState {
    val replies = it.comments.drop(1)
    CodeReviewFoldableThreadViewModel.RepliesStateData.Default(replies.mapNotNullTo(mutableSetOf()) { it.author },
                                                               replies.size,
                                                               replies.lastOrNull()?.createdAt)
  }

  override val canCreateReplies: StateFlow<Boolean> = dataState.mapState { it.viewerCanReply }
  override val newReplyVm: GHPRNewThreadCommentViewModel = ReplyViewModel()

  override val collapsible: StateFlow<Boolean> = isResolved
  private val _collapsed = MutableStateFlow(initialData.isResolved)
  override val collapsed: StateFlow<Boolean> = _collapsed.asStateFlow()
  private val _repliesFolded = MutableStateFlow(true)
  override val repliesFolded: StateFlow<Boolean> = _repliesFolded.asStateFlow()

  private val currentChanges = MutableStateFlow<GitBranchComparisonResult?>(null)

  init {
    dataProvider.changesData.loadChanges(cs.nestedDisposable()) {
      currentChanges.value = null
      cs.launchNow {
        currentChanges.value = try {
          it.await()
        }
        catch (e: Exception) {
          null
        }
      }
    }
  }

  val showDiffRequests = MutableSharedFlow<ChangesSelection>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  override fun changeResolvedState() {
    val resolved = isResolved.value
    taskLauncher.launch {
      val newData = try {
        if (resolved) {
          reviewData.unresolveThread(EmptyProgressIndicator(), id)
        }
        else {
          reviewData.resolveThread(EmptyProgressIndicator(), id)
        }.await()
      }
      catch (e: Exception) {
        if (e is ProcessCanceledException || e is CancellationException) return@launch
        LOG.warn("Failed to change thread resolution", e)
        return@launch
      }
      dataState.value = newData
      setCollapsed(newData.isResolved)
    }
  }

  override fun setCollapsed(collapsed: Boolean) {
    _collapsed.value = collapsed
    if (collapsed) {
      _repliesFolded.value = true
    }
  }

  override fun unfoldReplies() {
    _repliesFolded.value = false
    _collapsed.value = false
  }

  override fun showDiff() {
    val changes = currentChanges.value ?: return
    val thread = dataState.value
    val path = thread.path
    val commit = thread.commit
    val selection = if (commit == null || changes.headSha == commit.oid) {
      val change = changes.changes.findByFilePath(path) ?: return
      val location = thread.line?.let { DiffLineLocation(thread.side, it - 1) }
      ChangesSelection.Precise(changes.changes, change, location)
    }
    else {
      val commitChanges = changes.changesByCommits[commit.oid]
      val change = commitChanges?.findByFilePath(path) ?: return
      val location = thread.line?.let { DiffLineLocation(thread.side, it - 1) }
      ChangesSelection.Precise(commitChanges, change, location)
    }
    showDiffRequests.tryEmit(selection)
  }

  fun update(data: GHPullRequestReviewThread) {
    dataState.value = data
  }

  private fun calcDiffWithAnchor(thread: GHPullRequestReviewThread): Pair<PatchHunk, LineRange?> {
    val patchReader = PatchReader(PatchHunkUtil.createPatchFromHunk("_", thread.diffHunk))
    val hunk = patchReader.readTextPatches().firstOrNull()?.hunks?.firstOrNull() ?: TODO("Handle")

    val anchorLocation = thread.originalLine?.let { thread.side to it - 1 }
    val startAnchorLocation = if (thread.startSide != null && thread.originalStartLine != null) {
      thread.startSide to thread.originalStartLine - 1
    }
    else null

    val anchorLength = if (startAnchorLocation?.first == anchorLocation?.first) {
      ((anchorLocation?.second ?: 0) - (startAnchorLocation?.second ?: 0)).coerceAtLeast(0)
    }
    else {
      0
    }

    val hunkLength = anchorLength + TimelineDiffComponentFactory.DIFF_CONTEXT_SIZE
    val truncatedHunk = PatchHunkUtil.truncateHunkBefore(hunk, hunk.lines.lastIndex - hunkLength)

    val anchorRange = LineRange(truncatedHunk.lines.lastIndex - anchorLength, truncatedHunk.lines.size)
    return truncatedHunk to anchorRange
  }

  private fun CoroutineScope.createComment(comment: GHPullRequestReviewComment): UpdateableGHPRTimelineThreadCommentViewModel =
    UpdateableGHPRTimelineThreadCommentViewModel(project, this, dataContext, dataProvider,
                                                 this@UpdateableGHPRTimelineThreadViewModel, comment)

  private fun Collection<RefComparisonChange>.findByFilePath(path: String): RefComparisonChange? {
    val repoRoot = dataContext.repositoryDataService.remoteCoordinates.repository.root
    val filePath = VcsContextFactory.getInstance().createFilePath(repoRoot, path, false)
    return find {
      (it.filePathAfter != null && it.filePathAfter == filePath) || (it.filePathBefore != null && it.filePathBefore == filePath)
    }
  }

  private inner class ReplyViewModel
    : CodeReviewSubmittableTextViewModelBase(project, cs, ""), GHPRNewThreadCommentViewModel {

    override val currentUser: GHActor = dataContext.securityService.currentUser

    override fun submit() {
      val replyId = dataState.value.comments.firstOrNull()?.id ?: return
      submit {
        reviewData.addComment(EmptyProgressIndicator(), replyId, it).await()
        text.value = ""
      }
    }
  }
}