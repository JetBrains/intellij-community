// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.editor.EditorMapped
import com.intellij.collaboration.ui.codereview.editor.controlInlaysIn
import com.intellij.collaboration.util.ExcludingApproximateChangedRangesShifter
import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diff.DefaultFlagsProvider
import com.intellij.openapi.diff.LineStatusMarkerColorScheme
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ex.*
import com.intellij.openapi.vcs.ex.LineStatusMarkerPopupPanel.getEditorBackgroundColor
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.ui.ColorUtil
import com.intellij.ui.EditorTextField
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
import java.awt.Color
import java.awt.Graphics
import java.awt.Point
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
                showGutterMarkers(it, editor, lst)
                showInlays(it, editor, lst)
              }
            }
          }
      }
    }.cancelOnDispose(editorDisposable)
  }

  private fun CoroutineScope.showGutterMarkers(fileVm: GitLabMergeRequestChangeViewModel,
                                               editor: Editor,
                                               lst: LineStatusTrackerBase<*>) {
    val disposable = Disposer.newDisposable()
    val rangesSource = object : LineStatusMarkerRangesSource<LstRange> {
      var shiftedRanges: List<LstRange>? = null

      override fun isValid(): Boolean = shiftedRanges != null
      override fun getRanges(): List<LstRange>? = shiftedRanges
      override fun findRange(range: LstRange): LstRange? = shiftedRanges?.find {
        it.vcsLine1 == range.vcsLine1 && it.vcsLine2 == range.vcsLine2 &&
        it.line1 == range.line1 && it.line2 == range.line2
      }
    }
    val renderer = ReviewChangesGutterRenderer(fileVm, rangesSource, editor, disposable)
    LineStatusTrackerRangesHandler.install(disposable, lst) { lstRanges ->
      val reviewRanges = fileVm.changedRanges.map { LstRange(it.start2, it.end2, it.start1, it.end1) }
      rangesSource.shiftedRanges = ExcludingApproximateChangedRangesShifter.shift(reviewRanges, lstRanges)
      renderer.scheduleUpdate()
    }

    awaitCancellationAndInvoke {
      Disposer.dispose(disposable)
    }
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

private class ReviewChangesGutterRenderer(private val fileVm: GitLabMergeRequestChangeViewModel,
                                          rangesSource: LineStatusMarkerRangesSource<*>,
                                          editor: Editor,
                                          disposable: Disposable)
  : LineStatusMarkerRendererWithPopup(editor.project, editor.document, rangesSource, disposable, { it === editor }, false) {
  private val colorScheme = object : LineStatusMarkerColorScheme() {
    // TODO: extract color
    private val reviewChangesColor = ColorUtil.fromHex("#A177F4")

    override fun getColor(editor: Editor, type: Byte): Color = reviewChangesColor
    override fun getIgnoredBorderColor(editor: Editor, type: Byte): Color = reviewChangesColor
    override fun getErrorStripeColor(type: Byte): Color = reviewChangesColor
  }

  override fun paintGutterMarkers(editor: Editor, ranges: List<LstRange>, g: Graphics) {
    LineStatusMarkerDrawUtil.paintDefault(editor, g, ranges, DefaultFlagsProvider.DEFAULT, colorScheme, 0)
  }

  override fun createErrorStripeTextAttributes(diffType: Byte): TextAttributes = ReviewChangesTextAttributes(diffType)

  private inner class ReviewChangesTextAttributes(private val diffType: Byte) : TextAttributes() {
    override fun getErrorStripeColor(): Color? = colorScheme.getErrorStripeColor(diffType)
  }

  override fun createPopupPanel(editor: Editor,
                                range: LstRange,
                                mousePosition: Point?,
                                disposable: Disposable): LineStatusMarkerPopupPanel {
    val vcsContent = fileVm.getOriginalContent(LineRange(range.vcsLine1, range.vcsLine2))

    val editorComponent = if (!vcsContent.isNullOrEmpty()) {
      val popupEditor = createPopupEditor(project, editor, vcsContent, disposable)
      showLineDiff(editor, popupEditor, range, vcsContent, disposable)
      LineStatusMarkerPopupPanel.createEditorComponent(editor, popupEditor.component)
    }
    else {
      null
    }

    val toolbar = LineStatusMarkerPopupPanel.buildToolbar(editor, listOf(), disposable)
    return LineStatusMarkerPopupPanel.create(editor, toolbar, editorComponent, null)
  }

  private fun createPopupEditor(project: Project?, mainEditor: Editor, vcsContent: String, disposable: Disposable): Editor {
    val factory = EditorFactory.getInstance()
    val editor = factory.createViewer(factory.createDocument(vcsContent), project, EditorKind.DIFF) as EditorEx

    ReadAction.run<RuntimeException> {
      with(editor) {
        setCaretEnabled(false)
        getContentComponent().setFocusCycleRoot(false)

        setRendererMode(true)
        EditorTextField.setupTextFieldEditor(this)
        setVerticalScrollbarVisible(true)
        setHorizontalScrollbarVisible(true)
        setBorder(null)

        with(getSettings()) {
          setUseSoftWraps(false)
          setTabSize(mainEditor.getSettings().getTabSize(project))
          setUseTabCharacter(mainEditor.getSettings().isUseTabCharacter(project))
        }
        setColorsScheme(mainEditor.getColorsScheme())
        setBackgroundColor(getEditorBackgroundColor(mainEditor))

        getSelectionModel().removeSelection()
      }
    }
    disposable.whenDisposed {
      factory.releaseEditor(editor)
    }
    return editor
  }

  private fun showLineDiff(editor: Editor,
                           popupEditor: Editor,
                           range: LstRange, vcsContent: CharSequence,
                           disposable: Disposable) {
    if (vcsContent.isEmpty()) return

    val currentContent = DiffUtil.getLinesContent(editor.document, range.line1, range.line2)
    if (currentContent.isEmpty()) return

    val lineDiff = BackgroundTaskUtil.tryComputeFast({ indicator: ProgressIndicator? ->
                                                       ComparisonManager.getInstance().compareLines(vcsContent, currentContent,
                                                                                                    ComparisonPolicy.DEFAULT, indicator!!)
                                                     }, 200)
    if (lineDiff == null) return
    LineStatusMarkerPopupPanel.installMasterEditorWordHighlighters(editor, range.line1, range.line2, lineDiff, disposable)
    LineStatusMarkerPopupPanel.installEditorDiffHighlighters(popupEditor, lineDiff)
  }
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
    onRangesChanged(ranges.orEmpty())
  }

  override fun onOperationalStatusChange() {
    val ranges = lineStatusTracker.getRanges()
    if (ranges != null) {
      onRangesChanged(ranges)
    }
  }

  override fun onRangesChanged() {
    val ranges = lineStatusTracker.getRanges()
    if (ranges != null) {
      onRangesChanged(ranges)
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
