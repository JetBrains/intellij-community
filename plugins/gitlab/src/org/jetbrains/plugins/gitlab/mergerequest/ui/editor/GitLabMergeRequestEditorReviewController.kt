// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.editor.EditorMapped
import com.intellij.collaboration.ui.codereview.editor.controlInlaysIn
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ex.LineStatusTracker
import com.intellij.openapi.vcs.ex.LineStatusTrackerBase
import com.intellij.openapi.vcs.ex.LineStatusTrackerI
import com.intellij.openapi.vcs.ex.LineStatusTrackerListener
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.supervisorScope
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabToolWindowViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.diff.GitLabMergeRequestDiffInlayComponentsFactory
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestChangeViewModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabMergeRequestDiffDiscussionViewModel
import org.jetbrains.plugins.gitlab.ui.comment.NewGitLabNoteViewModel
import com.intellij.openapi.vcs.ex.Range as LstRange

@OptIn(ExperimentalCoroutinesApi::class)
@Service(Service.Level.PROJECT)
internal class GitLabMergeRequestEditorReviewController(private val project: Project, private val cs: CoroutineScope) {

  internal class InstallerListener : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
      val editor = event.editor
      editor.project?.service<GitLabMergeRequestEditorReviewController>()?.setupReview(editor)
    }
  }

  private fun setupReview(editor: Editor) {
    if (editor.editorKind != EditorKind.MAIN_EDITOR) return
    val file = editor.virtualFile ?: return

    val editorDisposable = Disposer.newDisposable().also {
      EditorUtil.disposeWithEditor(editor, it)
    }

    cs.launchNow(Dispatchers.Main) {
      editor.getLineStatusTrackerFlow().collectLatest { lst ->
        if (lst !is LineStatusTrackerBase<*>) return@collectLatest

        project.service<GitLabToolWindowViewModel>().projectVm
          .flatMapLatest {
            it?.currentMergeRequestReviewVm ?: flowOf(null)
          }.flatMapLatest {
            it?.getFileVm(file) ?: flowOf(null)
          }.collectLatest {
            if (it != null) {
              supervisorScope {
                showInlays(it, editor, lst)
              }
            }
          }
      }
    }.cancelOnDispose(editorDisposable)
  }

  /**
   * Show new and existing comments as editor inlays
   * Only comments located on the right diff side are shown
   * Comment locations are shifted to approximate position using [lst] ranges
   */
  private fun CoroutineScope.showInlays(fileVm: GitLabMergeRequestChangeViewModel, editor: Editor, lst: LineStatusTrackerBase<*>) {
    val cs = this
    editor as EditorEx
    val disposable = Disposer.newDisposable()

    val ranges = MutableStateFlow<List<LstRange>>(emptyList())
    LineStatusTrackerRangesHandler.install(disposable, lst) { lstRanges ->
      ranges.value = lstRanges
    }

    class MappedDiscussion(val vm: GitLabMergeRequestDiffDiscussionViewModel) : EditorMapped {
      override val isVisible: Flow<Boolean> = vm.isVisible
      override val line: Flow<Int?> = ranges.combine(vm.location) { ranges, location -> location?.let { mapLocation(ranges, it) } }
    }

    val discussions = fileVm.discussions.map { it.map(::MappedDiscussion) }
    editor.controlInlaysIn(cs, discussions, { it.vm.id }) {
      GitLabMergeRequestDiffInlayComponentsFactory.createDiscussion(
        project, this, fileVm.avatarIconsProvider, it.vm
      )
    }

    val draftDiscussions = fileVm.draftDiscussions.map { it.map(::MappedDiscussion) }
    editor.controlInlaysIn(cs, draftDiscussions, { it.vm.id }) {
      GitLabMergeRequestDiffInlayComponentsFactory.createDiscussion(
        project, this, fileVm.avatarIconsProvider, it.vm
      )
    }


    class MappedNewDiscussion(val location: DiffLineLocation, val vm: NewGitLabNoteViewModel) : EditorMapped {
      override val isVisible: Flow<Boolean> = flowOf(true)
      override val line: Flow<Int?> = ranges.map { ranges -> mapLocation(ranges, location) }
    }

    val newDiscussions = fileVm.newDiscussions.map {
      it.map { (location, vm) -> MappedNewDiscussion(location, vm) }
    }
    editor.controlInlaysIn(cs, newDiscussions, { "NEW_${it.location}" }) {
      GitLabMergeRequestDiffInlayComponentsFactory.createNewDiscussion(
        project, this, fileVm.avatarIconsProvider, it.vm
      ) { fileVm.cancelNewDiscussion(it.location) }
    }

    awaitCancellationAndInvoke {
      Disposer.dispose(disposable)
    }
  }
}

private fun mapLocation(ranges: List<LstRange>, location: DiffLineLocation): Int? {
  val (side, line) = location
  return if (side == Side.RIGHT) transferLine(ranges, line).takeIf { it >= 0 } else null
}

private fun transferLine(ranges: List<LstRange>, line: Int): Int {
  if (ranges.isEmpty()) return line
  var result = line
  for (range in ranges) {
    if (line in range.vcsLine1 until range.vcsLine2) {
      return (range.line2 - 1).coerceAtLeast(0)
    }

    if (range.vcsLine2 > line) return result

    val length1 = range.vcsLine2 - range.vcsLine1
    val length2 = range.line2 - range.line1
    result += length2 - length1
  }
  return result
}


private fun Editor.getLineStatusTrackerFlow(): Flow<LineStatusTracker<*>?> =
  callbackFlow {
    val lstm = LineStatusTrackerManager.getInstanceImpl(project!!)
    val listenerDisposable = Disposer.newDisposable()

    val lst = lstm.getLineStatusTracker(document)
    lstm.addTrackerListener(object : LineStatusTrackerManager.Listener {
      override fun onTrackerAdded(tracker: LineStatusTracker<*>) {
        if (tracker.document === document) {
          trySend(tracker)
        }
      }

      override fun onTrackerRemoved(tracker: LineStatusTracker<*>) {
        if (tracker.document === document) {
          trySend(null)
        }
      }
    }, listenerDisposable)
    send(lst)
    awaitClose {
      Disposer.dispose(listenerDisposable)
    }
  }.flowOn(Dispatchers.EDT)

private class LineStatusTrackerRangesHandler private constructor(
  private val lineStatusTracker: LineStatusTrackerI<*>,
  private val onRangesChanged: (List<LstRange>) -> Unit
) : LineStatusTrackerListener {
  init {
    val ranges = lineStatusTracker.getRanges()
    if (ranges == null || lineStatusTracker.isValid()) {
      onRangesChanged(ranges.orEmpty())
    }
  }

  override fun onOperationalStatusChange() {
    if (lineStatusTracker.isValid()) {
      onRangesChanged(lineStatusTracker.getRanges().orEmpty())
    }
  }

  override fun onRangesChanged() {
    if (lineStatusTracker.isValid()) {
      onRangesChanged(lineStatusTracker.getRanges().orEmpty())
    }
  }

  companion object {
    fun install(disposable: Disposable, lineStatusTracker: LineStatusTrackerBase<*>, onRangesChanged: (lstRanges: List<LstRange>) -> Unit) {
      val listener = LineStatusTrackerRangesHandler(lineStatusTracker, onRangesChanged)
      lineStatusTracker.addListener(listener)
      disposable.whenDisposed {
        lineStatusTracker.removeListener(listener)
      }
    }
  }
}
