// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.LineRange
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diff.DefaultFlagsProvider
import com.intellij.openapi.diff.LineStatusMarkerColorScheme
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ex.LineStatusMarkerPopupPanel
import com.intellij.openapi.vcs.ex.LineStatusMarkerRangesSource
import com.intellij.openapi.vcs.ex.LineStatusMarkerRendererWithPopup
import com.intellij.openapi.vcs.ex.Range
import com.intellij.ui.ColorUtil
import com.intellij.ui.EditorTextField
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestChangeViewModel
import java.awt.Color
import java.awt.Graphics
import java.awt.Point

/**
 * Draws and handles review changes markers in gutter
 */
internal class GitLabMergeRequestReviewChangesGutterRenderer(private val fileVm: GitLabMergeRequestChangeViewModel,
                                                             rangesSource: LineStatusMarkerRangesSource<*>,
                                                             editor: Editor,
                                                             disposable: Disposable)
  : LineStatusMarkerRendererWithPopup(editor.project, editor.document, rangesSource, disposable, { it === editor }) {
  private val colorScheme = object : LineStatusMarkerColorScheme() {
    // TODO: extract color
    private val reviewChangesColor = ColorUtil.fromHex("#A177F4")

    override fun getColor(editor: Editor, type: Byte): Color = reviewChangesColor
    override fun getIgnoredBorderColor(editor: Editor, type: Byte): Color = reviewChangesColor
    override fun getErrorStripeColor(type: Byte): Color = reviewChangesColor
  }

  override fun paintGutterMarkers(editor: Editor, ranges: List<Range>, g: Graphics) {
    LineStatusMarkerDrawUtil.paintDefault(editor, g, ranges, DefaultFlagsProvider.DEFAULT, colorScheme, 0)
  }

  override fun createErrorStripeTextAttributes(diffType: Byte): TextAttributes = ReviewChangesTextAttributes(diffType)

  private inner class ReviewChangesTextAttributes(private val diffType: Byte) : TextAttributes() {
    override fun getErrorStripeColor(): Color = colorScheme.getErrorStripeColor(diffType)
  }

  override fun createPopupPanel(editor: Editor,
                                range: Range,
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
        setBackgroundColor(LineStatusMarkerPopupPanel.getEditorBackgroundColor(mainEditor))

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
                           range: Range, vcsContent: CharSequence,
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