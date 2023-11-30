// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.ui.codereview.editor.ReviewInEditorUtil
import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.LineRange
import com.intellij.icons.AllIcons
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diff.DefaultFlagsProvider
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ex.LineStatusMarkerPopupActions
import com.intellij.openapi.vcs.ex.LineStatusMarkerPopupPanel
import com.intellij.openapi.vcs.ex.LineStatusMarkerRendererWithPopup
import com.intellij.openapi.vcs.ex.Range
import com.intellij.ui.EditorTextField
import org.jetbrains.plugins.gitlab.mergerequest.GitLabMergeRequestsPreferences
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import java.awt.Color
import java.awt.Graphics
import java.awt.Point
import java.awt.datatransfer.StringSelection

/**
 * Draws and handles review changes markers in gutter
 */
internal class GitLabMergeRequestReviewChangesGutterRenderer(private val model: GitLabMergeRequestEditorReviewUIModel,
                                                             private val editor: Editor,
                                                             disposable: Disposable)
  : LineStatusMarkerRendererWithPopup(editor.project, editor.document, model, disposable, { it === editor }) {

  override fun paintGutterMarkers(editor: Editor, ranges: List<Range>, g: Graphics) {
    LineStatusMarkerDrawUtil.paintDefault(editor, g, ranges, DefaultFlagsProvider.DEFAULT,
                                          ReviewInEditorUtil.REVIEW_STATUS_MARKER_COLOR_SCHEME, 0)
  }

  override fun createErrorStripeTextAttributes(diffType: Byte): TextAttributes = ReviewChangesTextAttributes()

  private inner class ReviewChangesTextAttributes : TextAttributes() {
    override fun getErrorStripeColor(): Color = ReviewInEditorUtil.REVIEW_CHANGES_STATUS_COLOR
  }

  override fun createPopupPanel(editor: Editor,
                                range: Range,
                                mousePosition: Point?,
                                disposable: Disposable): LineStatusMarkerPopupPanel {
    val vcsContent = model.getOriginalContent(LineRange(range.vcsLine1, range.vcsLine2))?.removeSuffix("\n")

    val preferences = project?.service<GitLabMergeRequestsPreferences>()

    val editorComponent = if (!vcsContent.isNullOrEmpty()) {
      val popupEditor = createPopupEditor(project, editor, vcsContent, disposable)
      if (preferences != null) {
        showLineDiff(preferences, editor, popupEditor, range, vcsContent, disposable)
      }
      LineStatusMarkerPopupPanel.createEditorComponent(editor, popupEditor.component)
    }
    else {
      null
    }

    val actions = mutableListOf<AnAction>(
      ShowPrevChangeMarkerAction(range),
      ShowNextChangeMarkerAction(range),
      CopyLineStatusRangeAction(range),
      ShowDiffAction(range)
    )
    if (preferences != null) {
      actions.add(ToggleByWordDiffAction(preferences))
    }

    val toolbar = LineStatusMarkerPopupPanel.buildToolbar(editor, actions, disposable)
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
        setBackgroundColor(LineStatusMarkerPopupPanel.getEditorBackgroundColor(mainEditor))

        getSelectionModel().removeSelection()
      }
    }
    disposable.whenDisposed {
      factory.releaseEditor(editor)
    }
    return editor
  }

  private fun showLineDiff(preferences: GitLabMergeRequestsPreferences,
                           editor: Editor,
                           popupEditor: Editor,
                           range: Range, vcsContent: CharSequence,
                           disposable: Disposable) {
    var highlightersDisposable: Disposable? = null
    fun update(show: Boolean) {
      if (show && highlightersDisposable == null) {
        if (vcsContent.isEmpty()) return

        val currentContent = DiffUtil.getLinesContent(editor.document, range.line1, range.line2)
        if (currentContent.isEmpty()) return

        val newDisposable = Disposer.newDisposable().also {
          Disposer.register(disposable, it)
        }
        highlightersDisposable = newDisposable

        val lineDiff = BackgroundTaskUtil.tryComputeFast({ indicator: ProgressIndicator? ->
                                                           ComparisonManager.getInstance().compareLines(vcsContent, currentContent,
                                                                                                        ComparisonPolicy.DEFAULT,
                                                                                                        indicator!!)
                                                         }, 200)
        if (lineDiff == null) return
        LineStatusMarkerPopupPanel.installMasterEditorWordHighlighters(editor, range.line1, range.line2, lineDiff, newDisposable)
        LineStatusMarkerPopupPanel.installEditorDiffHighlighters(popupEditor, lineDiff).also {
          newDisposable.whenDisposed {
            it.forEach(RangeHighlighter::dispose)
          }
        }
      }
      else {
        highlightersDisposable?.let(Disposer::dispose)
        highlightersDisposable = null
      }
    }

    preferences.addListener(disposable) { state: GitLabMergeRequestsPreferences.SettingsState ->
      update(state.highlightDiffLinesInEditor)
    }
    update(preferences.highlightDiffLinesInEditor)
  }

  private inner class ShowNextChangeMarkerAction(range: Range)
    : LineStatusMarkerPopupActions.RangeMarkerAction(editor, rangesSource, range, "VcsShowNextChangeMarker"), LightEditCompatible {

    override fun isEnabled(editor: Editor, range: Range): Boolean = getNextRange(range.line1) != null

    override fun actionPerformed(editor: Editor, range: Range) {
      val targetRange = getNextRange(range.line1)
      if (targetRange != null) {
        scrollAndShow(editor, targetRange)
      }
    }

    private fun getNextRange(line: Int): Range? {
      val ranges = rangesSource.getRanges() ?: return null
      return getNextRange(ranges, line)
    }
  }

  private inner class ShowPrevChangeMarkerAction(range: Range)
    : LineStatusMarkerPopupActions.RangeMarkerAction(editor, rangesSource, range, "VcsShowPrevChangeMarker"), LightEditCompatible {

    override fun isEnabled(editor: Editor, range: Range): Boolean = getPrevRange(range.line1) != null

    override fun actionPerformed(editor: Editor, range: Range) {
      val targetRange = getPrevRange(range.line1)
      if (targetRange != null) {
        scrollAndShow(editor, targetRange)
      }
    }

    private fun getPrevRange(line: Int): Range? {
      val ranges = rangesSource.getRanges()?.reversed() ?: return null
      return getNextRange(ranges, line)
    }
  }

  private inner class CopyLineStatusRangeAction(range: Range)
    : LineStatusMarkerPopupActions.RangeMarkerAction(editor, rangesSource, range, IdeActions.ACTION_COPY), LightEditCompatible {
    override fun isEnabled(editor: Editor, range: Range): Boolean = range.hasVcsLines()
    override fun actionPerformed(editor: Editor, range: Range) {
      val content = model.getOriginalContent(LineRange(range.vcsLine1, range.vcsLine2))
      CopyPasteManager.getInstance().setContents(StringSelection(content))
    }
  }

  private inner class ShowDiffAction(range: Range)
    : LineStatusMarkerPopupActions.RangeMarkerAction(editor, rangesSource, range, "Vcs.ShowDiffChangedLines"), LightEditCompatible {
    init {
      setShortcutSet(CompositeShortcutSet(KeymapUtil.getActiveKeymapShortcuts("Vcs.ShowDiffChangedLines"),
                                          KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_SHOW_DIFF_COMMON)))
      with(templatePresentation) {
        text = GitLabBundle.message("action.GitLab.Merge.Request.Review.Editor.Show.Diff.text")
        description = GitLabBundle.message("action.GitLab.Merge.Request.Review.Editor.Show.Diff.description")
      }
    }

    override fun isEnabled(editor: Editor, range: Range): Boolean = editor.getUserData(GitLabMergeRequestEditorReviewUIModel.KEY) != null
    override fun actionPerformed(editor: Editor, range: Range) {
      editor.getUserData(GitLabMergeRequestEditorReviewUIModel.KEY)?.showDiff(range.line1)
    }
  }

  private class ToggleByWordDiffAction(private val preferences: GitLabMergeRequestsPreferences)
    : ToggleAction(GitLabBundle.message("action.highlight.lines.text"), null, AllIcons.Actions.Highlighting),
      DumbAware, LightEditCompatible {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun isSelected(e: AnActionEvent): Boolean = preferences.highlightDiffLinesInEditor

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      preferences.highlightDiffLinesInEditor = state
    }
  }
}

private fun getNextRange(ranges: List<Range>, line: Int): Range? {
  var found = false
  for (range in ranges) {
    if (DiffUtil.isSelectedByLine(line, range.line1, range.line2)) {
      found = true
    }
    else if (found) {
      return range
    }
  }
  return null
}