@file:Suppress("DialogTitleCapitalization")

package com.intellij.grazie.ide.ui.mass

import com.intellij.diff.tools.util.FoldingModelSupport
import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.TextDiffTypeFactory.TextDiffTypeImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.icons.GrazieIcons
import com.intellij.grazie.ide.inspection.grammar.quickfix.GrazieReplaceTypoQuickFix.toRangeReplacements
import com.intellij.grazie.ide.ui.PaddedListCellRenderer
import com.intellij.grazie.text.CheckerRunner
import com.intellij.grazie.text.ProofreadingProblems
import com.intellij.grazie.text.TextProblem
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.CustomFoldRegionRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.IconUtil
import com.intellij.util.text.TextRangeUtil
import com.intellij.util.ui.JBUI
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Action
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import kotlin.math.max
import kotlin.math.min

private val appliedChange = Key<Int>("grazie.mass.apply.change.index")

class GrazieMassApplyDialog : DialogWrapper {
  private val text: String
  private val problems: ProofreadingProblems
  private val project: Project
  private val editor: EditorEx
  private val undoManager: DocumentUndoManager
  private val highlightings = HighlightedProblems()
  private val massOptionComboBox by lazy { CollectionComboBoxModel(MassOptions.entries) }

  constructor(file: PsiFile, problems: ProofreadingProblems) : super(file.project) {
    this.text = file.text
    this.project = file.project
    this.problems = problems
    this.editor = createEditor()
    this.undoManager = DocumentUndoManager()
    massApply(MassOptions.SINGLE)
    setupMouseListener()
    init()
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      title = GrazieBundle.message("grazie.mass.apply.dialog.title")

      row {
        comboBox(massOptionComboBox).gap(RightGap.SMALL)
          .onChanged { massApply(massOptionComboBox.selected) }
        cell(createToolbarComponent()).gap(RightGap.SMALL)

        label("").align(AlignX.FILL).resizableColumn()

        labeledIcon(GrazieIcons.StyleSuggestion) { it.style.size }
        labeledIcon(AllIcons.General.InspectionsGrammar) { it.grammar.size }
        labeledIcon(AllIcons.General.InspectionsTypos) { it.typos.size }
        label(GrazieBundle.message("grazie.mass.apply.dialog.suggestions")).gap(RightGap.SMALL)
        label(highlightings.suggestions.toString()).gap(RightGap.SMALL)
      }.bottomGap(BottomGap.SMALL)

      group(GrazieBundle.message("grazie.mass.apply.dialog.editor.title")) {
        row {
          cell(editor.component).align(Align.FILL)
        }
      }
    }
  }

  override fun createActions(): Array<Action> {
    return arrayOf(
      cancelAction,
      okAction.apply { putValue(Action.NAME, GrazieBundle.message("grazie.mass.apply.dialog.apply")) }
    )
  }

  private fun createToolbarComponent(): JComponent {
    val actionGroup = DefaultActionGroup().apply {
      add(createDoAction(true))
      add(createDoAction(false))
    }
    val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, actionGroup, true).apply {
      targetComponent = editor.component
    }
    return toolbar.component
  }

  private fun createDoAction(isUndo: Boolean): AnAction {
    val text =
      if (isUndo) GrazieBundle.message("grazie.mass.apply.dialog.editor.undo") else GrazieBundle.message("grazie.mass.apply.dialog.editor.redo")
    val icon = if (isUndo) AllIcons.Diff.Revert else IconUtil.flip(AllIcons.Diff.Revert, true)
    val undoAction = object : AnAction(text, null, icon) {
      override fun actionPerformed(e: AnActionEvent) {
        if (isUndo) undoManager.undo() else undoManager.redo()
      }

      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = if (isUndo) undoManager.isUndoAvailable() else undoManager.isRedoAvailable()
      }

      override fun getActionUpdateThread() = ActionUpdateThread.EDT
    }
    undoAction.registerCustomShortcutSet(
      KeymapUtil.getActiveKeymapShortcuts(if (isUndo) IdeActions.ACTION_UNDO else IdeActions.ACTION_REDO),
      editor.component,
      disposable
    )
    return undoAction
  }

  private fun createEditor(): EditorEx {
    val document = EditorFactory.getInstance().createDocument("")
    val editor = DiffUtil.createEditor(document, project, true, true)
    editor.component.minimumSize = JBUI.size(500, 500)
    editor.component.preferredSize = JBUI.size(800, 500)
    Disposer.register(disposable) { EditorFactory.getInstance().releaseEditor(editor) }
    return editor
  }

  private fun setupFolding(textRanges: List<TextRange>) {
    val lines = editor.document.lineCount - 1
    val textLineRanges = textRanges
      .map {
        TextRange(
          max(0, editor.document.getLineNumber(it.startOffset) - 2),
          min(editor.document.getLineNumber(it.endOffset) + 2, lines)
        )
      }
      .sortedBy { it.startOffset }
      .let { TextRangeUtil.mergeRanges(it, 1) }

    val renderer = object : CustomFoldRegionRenderer {
      override fun calcWidthInPixels(region: CustomFoldRegion) = 0
      override fun calcHeightInPixels(region: CustomFoldRegion) = 0
      override fun paint(region: CustomFoldRegion, g: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) {}
    }

    editor.foldingModel.runBatchFoldingOperation {
      editor.foldingModel.clearFoldRegions()
      for (region in TextRangeUtil.excludeRanges(TextRange(0, lines), textLineRanges)) {
        editor.foldingModel.addCustomLinesFolding(region.startOffset, region.endOffset, renderer)
        val currentRegion = FoldingModelSupport.addFolding(editor, region.startOffset, region.endOffset, false)
        if (currentRegion != null) {
          DiffDrawUtil.createLineSeparatorHighlighter(
            editor,
            editor.getDocument().getLineStartOffset(region.startOffset),
            editor.getDocument().getLineEndOffset(region.endOffset)
          )
        }
      }
    }
  }

  private fun setupHighlightings() {
    val document = editor.document
    addRangeHighlighter(editor, TextRange(0, document.textLength), BASE_TEXT_ATTRIBUTES)
    val textRanges = problems.textRanges
    textRanges.forEach { range -> addRangeHighlighter(editor, range, REGULAR_TEXT_ATTRIBUTES) }
    this.highlightings.update(editor, getHighlightings())
    setupFolding(textRanges)
  }

  private fun massApply(options: MassOptions?) {
    if (options == null) return
    val document = editor.document
    WriteCommandAction.runWriteCommandAction(project, GrazieBundle.message("grazie.mass.apply.text.do"), null, {
      document.replaceString(0, document.textLength, this.text)
    })
    setupHighlightings()
    highlightings.problems.forEach { highlighting ->
      val changes = highlighting.changes.first()
      if (options == MassOptions.MULTIPLE || highlighting.changes.filterIsInstance<DocumentChange>().size == 1) {
        changes.apply()
      }
    }
    undoManager.clear()
  }

  private fun setupMouseListener() {
    editor.addEditorMouseListener(object : EditorMouseListener {
      override fun mouseClicked(event: EditorMouseEvent) {
        val highlighting = highlightings.findHighlighting(event.offset) ?: return
        val popup = JBPopupFactory.getInstance()
          .createPopupChooserBuilder(highlighting.changes)
          .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
          .setRenderer(PaddedListCellRenderer())
          .setItemChosenCallback { chosenChange ->
            val changeIndex = highlighting.findChangeIndex()
            val chosenChangeIndex = highlighting.changes.indexOf(chosenChange)
            if (changeIndex == chosenChangeIndex) return@setItemChosenCallback

            WriteCommandAction.runWriteCommandAction(
              project,
              GrazieBundle.message("grazie.mass.apply.text.do"),
              null,
              {
                val change = if (changeIndex != null) highlighting.changes[changeIndex] else null
                val appliedChange = if (change != null) CompositeChange(change, chosenChange) else chosenChange
                appliedChange.apply()
                undoManager.trackChange(appliedChange)
              }
            )
          }
          .setNamerForFiltering { it.toString() }
          .createPopup()
        popup.showInBestPositionFor(editor)
      }
    })
  }

  private fun getHighlightings(): List<Highlighting> =
    problems.problems.map { problem ->
      val highlightRanges = problem.highlightRanges
        .map { problem.text.textRangeToFile(it) }
        .map { addRangeHighlighter(editor, it, BOLD_TEXT_ATTRIBUTES) }
      val changes = problem.suggestions.map { suggestion ->
        val replacements = suggestion.changes
          .flatMap { toRangeReplacements(it.range, it.replacement, problem) }
          .map { (range, replacement) -> range to replacement }
        DocumentChange(suggestion.presentableText, replacements, editor, project)
      }
      Highlighting(problem, highlightRanges, changes + IgnoreChange(problem, changes.firstOrNull()))
    }

  private fun Row.labeledIcon(icon: Icon, problemsExtractor: (HighlightedProblems) -> Int) {
    val problemCount = problemsExtractor(highlightings)
    icon(icon).gap(RightGap.SMALL).visible(problemCount > 0)
    label(problemCount.toString()).gap(RightGap.SMALL).visible(problemCount > 0)
  }

  fun apply(editor: Editor) {
    if (exitCode != OK_EXIT_CODE) return
    val replacements = mutableListOf<Pair<TextRange, String>>()
    highlightings.problems.forEach { highlighting ->
      val changeIndex = highlighting.findChangeIndex() ?: return@forEach
      val change = highlighting.changes[changeIndex]
      if (change !is DocumentChange) return@forEach
      replacements.addAll(change.originalReplacements)
    }
    replacements.sortByDescending { it.first.startOffset }
    WriteCommandAction.runWriteCommandAction(project, GrazieBundle.message("grazie.mass.apply.text.do"), null, {
      replacements.forEach { (range, text) ->
        editor.document.replaceString(range.startOffset, range.endOffset, text)
      }
    })
  }
}

private fun createInlineHighlighter(editor: Editor, range: TextRange, diffAttributes: Pair<Int, TextDiffTypeImpl>): RangeHighlighter {
  return DiffDrawUtil
    .createInlineHighlighter(editor, range.startOffset, range.endOffset, diffAttributes.first, diffAttributes.second)
    .first()
}

private fun addRangeHighlighter(editor: Editor, range: TextRange, textAttribute: Pair<Int, TextAttributesKey>): RangeHighlighter {
  return editor.markupModel.addRangeHighlighter(
    textAttribute.second,
    range.startOffset,
    range.endOffset,
    textAttribute.first,
    HighlighterTargetArea.EXACT_RANGE
  )
}

private enum class ChangeType {
  DELETE,
  INSERT,
  MODIFY
}

private interface Change {
  fun apply()
  fun revert()
}

private class CompositeChange(private val oldChange: Change, private val newChange: Change) : Change {
  override fun apply() {
    oldChange.revert()
    newChange.apply()
  }

  override fun revert() {
    newChange.revert()
    oldChange.apply()
  }
}

private class IgnoreChange : Change {
  private val change: Change?
  private val presentableText: String

  constructor(problem: TextProblem, change: Change?) {
    val suppressionPattern = CheckerRunner(problem.text).defaultSuppressionPattern(problem, null)
    val errorText = StringUtil.shortenTextWithEllipsis(suppressionPattern.errorText, 50, 20)
    this.presentableText = GrazieBundle.message("grazie.grammar.quickfix.ignore.text.no.context", errorText)
    this.change = change
  }

  override fun apply() {
    change?.revert()
  }

  override fun revert() {
    change?.apply()
  }

  override fun toString(): String = presentableText
}

private class DocumentChange : Change {
  private val presentableText: String
  private val replacements: List<Pair<RangeMarker, String>>
  val originalReplacements: List<Pair<TextRange, String>>
  private val editor: Editor
  private val project: Project
  private val revertChanges = mutableListOf<RevertChange>()
  private var highlighting: Highlighting? = null

  constructor(presentableText: String, replacements: List<Pair<TextRange, String>>, editor: Editor, project: Project) {
    this.presentableText = presentableText
    this.replacements = replacements.map { editor.document.createRangeMarker(it.first) to it.second }
    this.originalReplacements = replacements
    this.editor = editor
    this.project = project
  }

  fun setHighlighting(highlighting: Highlighting) {
    this.highlighting = highlighting
  }

  override fun apply() {
    WriteCommandAction.runWriteCommandAction(project, GrazieBundle.message("grazie.mass.apply.text.do"), null, {
      replacements.asReversed().forEach { (marker, replacement) ->
        val oldText = editor.document.getText(marker.textRange)
        val type = getChangeType(marker, replacement)
        if (type != ChangeType.DELETE) {
          editor.document.replaceString(marker.startOffset, marker.endOffset, replacement)
        }
        revertChanges.add(RevertChange(oldText, type, createRangeHighlighter(editor, type, marker, replacement)))
      }
    })
    highlighting!!.setChange(this)
  }

  override fun revert() {
    WriteCommandAction.runWriteCommandAction(project, GrazieBundle.message("grazie.mass.apply.text.do"), null, {
      revertChanges.asReversed().forEach { info ->
        editor.markupModel.removeHighlighter(info.range)
        if (info.type != ChangeType.DELETE) {
          val range = info.range.textRange
          editor.document.replaceString(range.startOffset, range.endOffset, info.oldText)
        }
      }
    })
    revertChanges.clear()
    highlighting!!.removeChange()
  }

  override fun toString(): String = presentableText

  fun clear(editor: Editor) {
    revertChanges.forEach { editor.markupModel.removeHighlighter(it.range) }
    revertChanges.clear()
  }

  private fun getChangeType(marker: RangeMarker, text: String): ChangeType =
    if (marker.textRange.isEmpty) {
      ChangeType.INSERT
    }
    else if (text.isEmpty()) {
      ChangeType.DELETE
    }
    else {
      ChangeType.MODIFY
    }

  private fun createRangeHighlighter(editor: Editor, changeType: ChangeType, marker: RangeMarker, replacement: String): RangeHighlighter {
    val range = if (changeType == ChangeType.INSERT) marker.textRange.grown(replacement.length) else marker.textRange
    if (changeType == ChangeType.DELETE) return addRangeHighlighter(editor, range, STRIKEOUT_TEXT_ATTRIBUTES)
    return if (highlighting!!.problem.isSpellingProblem) {
      createInlineHighlighter(editor, range, TYPO_TEXT_DIFF)
    } else if (highlighting!!.problem.isStyleLike) {
      createInlineHighlighter(editor, range, STYLE_TEXT_DIFF)
    } else {
      createInlineHighlighter(editor, range, GRAMMAR_TEXT_DIFF)
    }
  }

  private data class RevertChange(
    val oldText: String,
    val type: ChangeType,
    val range: RangeHighlighter,
  )
}

private data class Highlighting(val problem: TextProblem, val ranges: List<RangeHighlighter>, val changes: List<Change>) {
  init {
    changes.filterIsInstance<DocumentChange>().forEach { it.setHighlighting(this) }
  }

  fun findChangeIndex(): Int? = ranges.first().getUserData(appliedChange)

  fun setChange(change: Change) {
    ranges.first().putUserData(appliedChange, changes.indexOf(change))
  }

  fun removeChange() {
    ranges.first().putUserData(appliedChange, null)
  }

  fun clear(editor: Editor) {
    removeChange()
    ranges.forEach { editor.markupModel.removeHighlighter(it) }
    changes.filterIsInstance<DocumentChange>().forEach { it.clear(editor) }
  }
}

private data class HighlightedProblems(val problems: MutableList<Highlighting> = mutableListOf()) {
  val suggestions: Int
    get() = problems.sumOf { it.problem.suggestions.asSequence().take(15).count() }

  fun update(editor: Editor, problems: List<Highlighting>) {
    this.problems.forEach { it.clear(editor) }
    this.problems.clear()
    this.problems.addAll(problems)
  }

  fun findHighlighting(offset: Int): Highlighting? =
    problems.firstOrNull { problem -> problem.ranges.any { it.startOffset <= offset && it.endOffset >= offset } }

  val style: List<Highlighting>
    get() = problems.filter { it.problem.isStyleLike }

  val typos: List<Highlighting>
    get() = problems.filter { it.problem.isSpellingProblem }

  val grammar: List<Highlighting>
    get() = problems.filter { !it.problem.isStyleLike && !it.problem.isSpellingProblem }
}

private class DocumentUndoManager {
  private val undoStack = LinkedList<Change>()
  private val redoStack = LinkedList<Change>()
  private var isDoInProgress = AtomicBoolean(false)

  fun trackChange(changes: Change) {
    if (isDoInProgress.get()) return
    undoStack.add(changes)
    redoStack.clear()
  }

  fun clear() {
    if (isDoInProgress.get()) return
    undoStack.clear()
    redoStack.clear()
  }

  fun undo() {
    if (!isUndoAvailable()) return
    if (!isDoInProgress.compareAndSet(false, true)) return
    try {
      val change = undoStack.removeLast()
      change.revert()
      redoStack.add(change)
    }
    finally {
      isDoInProgress.compareAndSet(true, false)
    }
  }

  fun redo() {
    if (!isRedoAvailable()) return
    if (!isDoInProgress.compareAndSet(false, true)) return
    try {
      val change = redoStack.removeLast()
      change.apply()
      undoStack.add(change)
    }
    finally {
      isDoInProgress.compareAndSet(true, false)
    }
  }

  fun isUndoAvailable(): Boolean = undoStack.isNotEmpty()

  fun isRedoAvailable(): Boolean = redoStack.isNotEmpty()
}

private val TEXT_BOLD = TextAttributesKey.createTextAttributesKey(
  "TEXT_BOLD",
  TextAttributes().apply { fontType = Font.BOLD }
)

private val TEXT_STRIKEOUT = TextAttributesKey.createTextAttributesKey(
  "TEXT_STRIKEOUT",
  TextAttributes().apply {
    effectType = EffectType.STRIKEOUT
    effectColor = JBColor.foreground()
    foregroundColor = JBColor.GRAY
  }
)

private object StyleDiff : TextDiffTypeImpl(
  TextAttributesKey.createTextAttributesKey("CONSOLE_BLUE_OUTPUT"),
  GrazieBundle.message("grazie.mass.apply.text.diff.style.name")
)

private object GrammarDiff : TextDiffTypeImpl(
  TextAttributesKey.createTextAttributesKey("CONSOLE_RED_OUTPUT"),
  GrazieBundle.message("grazie.mass.apply.text.diff.grammar.name"),
)

private object TypoDiff : TextDiffTypeImpl(
  TextAttributesKey.createTextAttributesKey("CONSOLE_GREEN_OUTPUT"),
  GrazieBundle.message("grazie.mass.apply.text.diff.typo.name")
)

private val BASE_TEXT_ATTRIBUTES = 0 to ConsoleViewContentType.LOG_EXPIRED_ENTRY
private val REGULAR_TEXT_ATTRIBUTES = 10 to HighlighterColors.TEXT
private val BOLD_TEXT_ATTRIBUTES = 20 to TEXT_BOLD
private val STRIKEOUT_TEXT_ATTRIBUTES = 30 to TEXT_STRIKEOUT
private val STYLE_TEXT_DIFF = 30 to StyleDiff
private val GRAMMAR_TEXT_DIFF = 30 to GrammarDiff
private val TYPO_TEXT_DIFF = 30 to TypoDiff

private enum class MassOptions(@param:NlsSafe val text: String) {
  SINGLE(GrazieBundle.message("grazie.mass.apply.dialog.single")),
  MULTIPLE(GrazieBundle.message("grazie.mass.apply.dialog.multiple"));

  override fun toString(): String = text
}