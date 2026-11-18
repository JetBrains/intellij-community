// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.async.combineStates
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.async.transformConsecutiveSuccesses
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.diff.UnifiedCodeReviewItemPosition
import com.intellij.collaboration.ui.codereview.editor.CodeReviewInEditorViewModel
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.selectedItem
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.getOrNull
import com.intellij.diff.util.Side
import com.intellij.openapi.ListSelection
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.EventDispatcher
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.data.GitLabImageLoader
import org.jetbrains.plugins.gitlab.mergerequest.GitLabReviewSettings
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabLocalRepository
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestParsedChanges
import org.jetbrains.plugins.gitlab.mergerequest.ui.createDiffDataFlow
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestDiscussionsViewModels
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestReviewViewModelBase
import org.jetbrains.plugins.gitlab.util.GitLabStatistics
import java.util.EventListener

private val LOG = logger<GitLabMergeRequestEditorReviewViewModel>()

@ApiStatus.Internal
class GitLabMergeRequestEditorReviewViewModel internal constructor(
  parentCs: CoroutineScope,
  project: Project,
  private val localRepository: GitLabLocalRepository,
  currentUser: GitLabUserDTO,
  private val mergeRequest: GitLabMergeRequest,
  private val discussionsVms: GitLabMergeRequestDiscussionsViewModels,
  private val avatarIconsProvider: IconsProvider<GitLabUserDTO>,
  private val imageLoader: GitLabImageLoader,
  private val openMergeRequestDetails: (String, GitLabStatistics.ToolWindowOpenTabActionPlace, Boolean) -> Unit,
  private val openMergeRequestDiff: (String, Boolean) -> Unit,
) : GitLabMergeRequestReviewViewModelBase(
  parentCs.childScope("GitLab Merge Request Editor Review VM"),
  project, currentUser, mergeRequest,
  if (GitLabReviewSettings.getInstance(project).editorReviewEnabled) DiscussionsViewOption.UNRESOLVED_ONLY else DiscussionsViewOption.DONT_SHOW
), CodeReviewInEditorViewModel {
  private val preferences = GitLabReviewSettings.getInstance(project)

  val mergeRequestIid: String = mergeRequest.iid

  private val _actualChangesState = MutableStateFlow<ChangesState>(ChangesState.NotLoaded)
  private val changesRequest = MutableSharedFlow<Unit>(replay = 1)

  val actualChangesState: StateFlow<ChangesState> = _actualChangesState.asStateFlow()
  private val actualChanges: StateFlow<GitLabMergeRequestParsedChanges?> = actualChangesState.mapNotNull {
    (it as? ChangesState.Loaded)?.changes
  }.distinctUntilChangedBy {
    it.baseSha + it.headSha + it.mergeBaseSha
  }.stateInNow(cs, null)
  private val patchesByChangeFlow = actualChanges.mapState { changesOrNull ->
    val changes = changesOrNull ?: return@mapState null
    val allChanges = changes.changes.toSet()

    changes.patchesByChange.filterKeys { it in allChanges }
  }

  private val filesVms: MutableMap<FilePath, Flow<FileReviewState>> = mutableMapOf()
  private val diffRequestsMulticaster = EventDispatcher.create(DiffRequestListener::class.java)

  internal val discussions: StateFlow<ComputedResult<Collection<GitLabMergeRequestEditorDiscussionViewModel>>> =
    discussionsVms.discussions.transformConsecutiveSuccesses {
      map { discussions ->
        discussions.map { discussion ->
          val diffDataFlow = createDiffDataFlow(discussion.position, patchesByChangeFlow)
          GitLabMergeRequestEditorDiscussionViewModel(discussion, diffDataFlow, discussionsViewOption)
        }
      }
    }.stateInNow(cs, ComputedResult.loading())
  internal val draftNotes: StateFlow<ComputedResult<Collection<GitLabMergeRequestEditorDraftNoteViewModel>>> =
    discussionsVms.draftNotes.transformConsecutiveSuccesses {
      map { draftNotes ->
        draftNotes.map { draftNote ->
          val diffDataFlow = createDiffDataFlow(draftNote.position, patchesByChangeFlow)
          GitLabMergeRequestEditorDraftNoteViewModel(draftNote, diffDataFlow, discussionsViewOption)
        }
      }
    }.stateInNow(cs, ComputedResult.loading())
  internal val newDiscussions: StateFlow<Collection<GitLabMergeRequestEditorNewDiscussionViewModel>> =
    discussionsVms.newDiscussions.map { newDiscussions ->
      newDiscussions.map { vm ->
        val diffDataFlow = createDiffDataFlow(vm.position.mapState { it.position }, patchesByChangeFlow)
        GitLabMergeRequestEditorNewDiscussionViewModel(vm, diffDataFlow, discussionsViewOption)
      }
    }.stateInNow(cs, emptyList())

  private val noteByTrackingId: StateFlow<Map<String, DiffDataMappedGitLabMergeRequestEditorViewModel>> =
    combineStates(discussions, draftNotes, newDiscussions) { discussionsResult, draftNotesResult, newDiscussions ->
      val discussions = discussionsResult.getOrNull() ?: emptyList()
      val draftNotes = draftNotesResult.getOrNull() ?: emptyList()

      buildList {
        addAll(discussions)
        addAll(draftNotes)
        addAll(newDiscussions)
      }.associateBy { note -> note.trackingId }
    }

  /**
   * Whether the local branch misses some commits of the merge request, or `null` while this is unknown.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  val hasIncomingCommits: StateFlow<ComputedResult<Boolean>?> by lazy {
    _actualChangesState.map {
      (it as? ChangesState.Loaded)?.changes?.commits?.map { it.sha }
    }.distinctUntilChanged().transformLatest {
      if (it == null) emit(null)
      else localRepository.hasIncomingCommits(it).collect(this)
    }.stateIn(cs, SharingStarted.Eagerly, null)
  }

  override val updateRequired: StateFlow<Boolean> = hasIncomingCommits.mapState { it?.getOrNull() == true }

  init {
    cs.launchNow {
      mergeRequest.changes.collectLatest { changes ->
        _actualChangesState.value = ChangesState.NotLoaded
        changesRequest.distinctUntilChanged().collectLatest {
          try {
            _actualChangesState.value = ChangesState.Loading
            val parsed = changes.getParsedChanges()
            _actualChangesState.value = ChangesState.Loaded(parsed)
          }
          catch (ce: CancellationException) {
            _actualChangesState.value = ChangesState.NotLoaded
          }
          catch (e: Exception) {
            _actualChangesState.value = ChangesState.Error
          }
        }
      }
    }
  }

  /**
   * Show merge request details in a standard view
   */
  fun showMergeRequest(place: GitLabStatistics.ToolWindowOpenTabActionPlace) {
    openMergeRequestDetails(mergeRequestIid, place, true)
  }

  override fun updateBranch() {
    cs.launch {
      val details = mergeRequest.refreshDataNow()
      localRepository.checkoutBranch(details)
    }
  }

  fun toggleReviewMode(enabled: Boolean) {
    val newOption = if (enabled) DiscussionsViewOption.UNRESOLVED_ONLY else DiscussionsViewOption.DONT_SHOW
    setDiscussionsViewOption(newOption)
  }

  override fun setDiscussionsViewOption(viewOption: DiscussionsViewOption) {
    super.setDiscussionsViewOption(viewOption)
    preferences.editorReviewEnabled = viewOption != DiscussionsViewOption.DONT_SHOW
  }

  fun lookupNextComment(cursorLocation: UnifiedCodeReviewItemPosition, additionalIsVisible: (String) -> Boolean): String? =
    discussionsVms.lookupNextComment(cursorLocation) { isNoteVisible(it) && additionalIsVisible(it) }

  fun lookupNextComment(noteTrackingId: String, additionalIsVisible: (String) -> Boolean): String? =
    discussionsVms.lookupNextComment(noteTrackingId) { isNoteVisible(it) && additionalIsVisible(it) }

  fun lookupPreviousComment(cursorLocation: UnifiedCodeReviewItemPosition, additionalIsVisible: (String) -> Boolean): String? =
    discussionsVms.lookupPreviousComment(cursorLocation) { isNoteVisible(it) && additionalIsVisible(it) }

  fun lookupPreviousComment(noteTrackingId: String, additionalIsVisible: (String) -> Boolean): String? =
    discussionsVms.lookupPreviousComment(noteTrackingId) { isNoteVisible(it) && additionalIsVisible(it) }

  internal fun lookupThreadPosition(noteTrackingId: String): Pair<RefComparisonChange, Int>? {
    val note = noteByTrackingId.value[noteTrackingId] ?: return null

    val change = note.diffData.value?.change ?: return null
    val line = note.line.value ?: return null

    return change to line
  }

  internal fun requestThreadFocus(noteTrackingId: String) {
    val note = noteByTrackingId.value[noteTrackingId] ?: return
    note.requestFocus()
  }

  private fun isNoteVisible(noteTrackingId: String): Boolean {
    val note = noteByTrackingId.value[noteTrackingId] ?: return false
    return note.isVisible.value && note.line.value != null
  }

  /**
   * A view model for [virtualFile] review
   */
  fun getFileStateFlow(virtualFile: VirtualFile): Flow<FileReviewState> = flow {
    val filePath = localRepository.findFilePath(virtualFile)
    if (filePath == null) {
      emit(FileReviewState.NotInReview)
    }
    else {
      changesRequest.tryEmit(Unit)
      emitAll(getFileStateFlow(filePath))
    }
  }

  private fun getFileStateFlow(filePath: FilePath): Flow<FileReviewState> =
    //TODO: do not recreate VMs on changes change
    filesVms.getOrPut(filePath) {
      actualChanges.filterNotNull().mapScoped { parsedChanges ->
        val change = parsedChanges.changes.find { it.filePathAfter == filePath }
        if (change == null) {
          return@mapScoped FileReviewState.NotInReview
        }
        val diffData = parsedChanges.patchesByChange[change] ?: run {
          LOG.info("Diff data not found for change $change")
          return@mapScoped FileReviewState.NotInReview
        }
        if (diffData.patch.hunks.isEmpty()) {
          // a file whose empty diff is correct carries no reason, so it shows no warning
          val reason = parsedChanges.getEmptyPatchReason(change) ?: return@mapScoped FileReviewState.NotInReview
          return@mapScoped FileReviewState.ReviewDisabled(reason)
        }

        val changeSelection = ListSelection.create(parsedChanges.changes, change)
        FileReviewState.ReviewEnabled(createChangeVm(changeSelection, diffData))
      }
    }

  suspend fun handleDiffRequests(listener: (ListSelection<RefComparisonChange>, DiffLineLocation?) -> Unit): Nothing {
    val actualListener = DiffRequestListener { list, location -> listener(list, location) }
    try {
      diffRequestsMulticaster.addListener(actualListener)
      awaitCancellation()
    }
    finally {
      diffRequestsMulticaster.removeListener(actualListener)
    }
  }

  private fun CoroutineScope.createChangeVm(changes: ListSelection<RefComparisonChange>, diffData: GitTextFilePatchWithHistory) =
    GitLabMergeRequestEditorReviewFileViewModelImpl(
      this, mergeRequest, localRepository, changes.selectedItem!!, diffData,
      discussionsVms, this@GitLabMergeRequestEditorReviewViewModel,
      discussionsViewOption, avatarIconsProvider, imageLoader
    ).also { vm ->
      launchNow {
        vm.showDiffRequests.collect { line ->
          diffRequestsMulticaster.multicaster.onChangesSelectionChanged(changes, line?.let { DiffLineLocation(Side.RIGHT, it) })
          withContext(Dispatchers.Main) {
            openMergeRequestDiff(mergeRequestIid, true)
          }
        }
      }
    }

  sealed interface ChangesState {
    data object NotLoaded : ChangesState
    data object Loading : ChangesState
    data object Error : ChangesState
    class Loaded(val changes: GitLabMergeRequestParsedChanges) : ChangesState
  }

  sealed interface FileReviewState {
    data object NotInReview : FileReviewState
    data class ReviewDisabled(val reason: GitLabMergeRequestParsedChanges.EmptyPatchReason) : FileReviewState
    data class ReviewEnabled(val vm: GitLabMergeRequestEditorReviewFileViewModel) : FileReviewState
  }
}

private fun interface DiffRequestListener : EventListener {
  fun onChangesSelectionChanged(changes: ListSelection<RefComparisonChange>, location: DiffLineLocation?)
}