package com.intellij.cce.interpreter

import com.intellij.cce.actions.CompletionGolfEmulation
import com.intellij.cce.actions.TextRange
import com.intellij.cce.actions.UserEmulator
import com.intellij.cce.actions.selectedWithoutPrefix
import com.intellij.cce.core.*
import com.intellij.cce.evaluation.CodeCompletionHandlerFactory
import com.intellij.cce.evaluation.SuggestionsProvider
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionProgressIndicator
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.codeInsight.lookup.*
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.actions.MLCompletionFeaturesUtil
import com.intellij.completion.ml.util.prefix
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.TrailingSpacesStripper
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.TestModeFlags
import com.intellij.ui.icons.CachedImageIcon
import java.io.File
import kotlin.random.Random

class CompletionInvokerImpl(private val project: Project,
                            private val language: Language,
                            completionType: com.intellij.cce.actions.CompletionType,
                            userEmulationSettings: UserEmulator.Settings?,
                            private val completionGolfSettings: CompletionGolfEmulation.Settings?) : CompletionInvoker {
  private var benchmarkRandom = resetRandom()

  private companion object {
    val LOG = Logger.getInstance(CompletionInvokerImpl::class.java)
    const val LOG_MAX_LENGTH = 50
  }

  init {
    TestModeFlags.set(CompletionAutoPopupHandler.ourTestingAutopopup, true)
  }

  private val completionType = when (completionType) {
    com.intellij.cce.actions.CompletionType.SMART -> CompletionType.SMART
    else -> CompletionType.BASIC
  }
  private var editor: Editor? = null
  private var spaceStrippingEnabled: Boolean = true
  private val userEmulator: UserEmulator = UserEmulator.create(userEmulationSettings)
  private val dumbService = DumbService.getInstance(project)

  override fun moveCaret(offset: Int) {
    LOG.info("Move caret. ${positionToString(offset)}")
    editor!!.caretModel.moveToOffset(offset)
    editor!!.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
  }

  override fun callCompletion(expectedText: String, prefix: String?): com.intellij.cce.core.Lookup {
    LOG.info("Call completion. Type: $completionType. ${positionToString(editor!!.caretModel.offset)}")
//        assert(!dumbService.isDumb) { "Calling completion during indexing." }

    val start = System.currentTimeMillis()
    val isNew = LookupManager.getActiveLookup(editor) == null
    val activeLookup = LookupManager.getActiveLookup(editor) ?: invokeCompletion(expectedText, prefix)
    val latency = System.currentTimeMillis() - start
    if (activeLookup == null) {
      return com.intellij.cce.core.Lookup.fromExpectedText(expectedText, prefix ?: "", emptyList(), latency, isNew = isNew)
    }

    val lookup = activeLookup as LookupImpl
    val features = MLCompletionFeaturesUtil.getCommonFeatures(lookup)
    val resultFeatures = Features(
      CommonFeatures(features.context, features.user, features.session),
      lookup.items.map { MLCompletionFeaturesUtil.getElementFeatures(lookup, it).features }
    )
    val suggestions = lookup.items.map { it.asSuggestion(lookup) }

    return com.intellij.cce.core.Lookup.fromExpectedText(expectedText, lookup.prefix(), suggestions, latency, resultFeatures, isNew)
  }

  override fun finishCompletion(expectedText: String, prefix: String): Boolean {
    LOG.info("Finish completion. Expected text: $expectedText")
    if (completionType == CompletionType.SMART) return false
    val lookup = LookupManager.getActiveLookup(editor) as? LookupImpl ?: return false
    val expectedItemIndex = lookup.items.indexOfFirst { it.lookupString == expectedText }
    try {
      return if (expectedItemIndex != -1) lookup.finish(expectedItemIndex, expectedText.length - prefix.length) else false
    }
    finally {
      lookup.hide()
    }
  }

  override fun printText(text: String) {
    LOG.info("Print text: ${StringUtil.shortenPathWithEllipsis(text, LOG_MAX_LENGTH)}. ${positionToString(editor!!.caretModel.offset)}")
    val project = editor!!.project
    val runnable = Runnable { EditorModificationUtil.insertStringAtCaret(editor!!, text) }
    WriteCommandAction.runWriteCommandAction(project) {
      val lookup = LookupManager.getActiveLookup(editor) as? LookupImpl
      if (lookup != null) {
        lookup.replacePrefix(lookup.additionalPrefix, lookup.additionalPrefix + text)
      }
      else {
        runnable.run()
      }
    }
  }

  override fun deleteRange(begin: Int, end: Int) {
    val document = editor!!.document
    val textForDelete = StringUtil.shortenPathWithEllipsis(document.text.substring(begin, end), LOG_MAX_LENGTH)
    LOG.info("Delete range. Text: $textForDelete. Begin: ${positionToString(begin)} End: ${positionToString(end)}")
    val project = editor!!.project
    val runnable = Runnable { document.deleteString(begin, end) }
    WriteCommandAction.runWriteCommandAction(project, runnable)
    if (editor!!.caretModel.offset != begin) editor!!.caretModel.moveToOffset(begin)
  }

  override fun openFile(file: String): String {
    LOG.info("Open file: $file")
    benchmarkRandom = resetRandom()
    val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(File(file))
    val descriptor = OpenFileDescriptor(project, virtualFile!!)
    spaceStrippingEnabled = TrailingSpacesStripper.isEnabled(virtualFile)
    TrailingSpacesStripper.setEnabled(virtualFile, false)
    val fileEditor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
                     ?: throw Exception("Can't open text editor for file: $file")
    editor = fileEditor
    return fileEditor.document.text
  }

  override fun closeFile(file: String) {
    LOG.info("Close file: $file")
    val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(File(file))!!
    TrailingSpacesStripper.setEnabled(virtualFile, spaceStrippingEnabled)
    FileEditorManager.getInstance(project).closeFile(virtualFile)
    editor = null
  }

  override fun isOpen(file: String): Boolean {
    val isOpen = FileEditorManager.getInstance(project).openFiles.any { it.path == file }
    return isOpen
  }

  override fun save() {
    val document = editor?.document ?: throw IllegalStateException("No open editor")
    FileDocumentManager.getInstance().saveDocumentAsIs(document)
  }

  override fun getText(): String = editor?.document?.text ?: throw IllegalStateException("No open editor")

  override fun emulateUserSession(expectedText: String, nodeProperties: TokenProperties, offset: Int): Session {
    val editorImpl = editor as EditorImpl
    val session = Session(offset, expectedText, expectedText.length, null, nodeProperties)
    val firstPrefixLen = userEmulator.firstPrefixLen()
    if (firstPrefixLen >= expectedText.length) {
      printText(expectedText)
      return session
    }
    var currentPrefix = expectedText.substring(0, firstPrefixLen)
    for (ch in currentPrefix) editorImpl.type(ch.toString())
    var order = 0
    while (currentPrefix.length < expectedText.length) {
      val position = callCompletion(expectedText, currentPrefix)
        .also { session.addLookup(it) }
        .selectedPosition

      if (position != -1 && userEmulator.selectElement(position, currentPrefix.length)) {
        val success = finishCompletion(expectedText, currentPrefix)
        if (!success) printText(expectedText.substring(currentPrefix.length))
        session.success = success
        break
      }
      editorImpl.type(expectedText[currentPrefix.length].toString())
      currentPrefix += expectedText[currentPrefix.length]
      order++
    }
    hideLookup()
    return session
  }

  override fun emulateCompletionGolfSession(expectedLine: String, completableRanges: List<TextRange>, offset: Int): Session {
    val settings = completionGolfSettings ?: throw IllegalStateException("No completion golf settings in completion golf mode")
    val session = Session(offset, expectedLine, completableRanges.sumOf { it.end - it.start }, null, TokenProperties.UNKNOWN)
    if (settings.isBenchmark) {
      session.benchmark(expectedLine, completableRanges, offset, settings)
    }
    else {
      session.emulateCG(expectedLine, completableRanges, offset, settings)
    }
    return session
  }

  private fun Session.benchmark(expectedLine: String,
                                completableRanges: List<TextRange>,
                                offset: Int,
                                settings: CompletionGolfEmulation.Settings) {
    val emulator = CompletionGolfEmulation.createFromSettings(completionGolfSettings, expectedLine)
    for (range in completableRanges) {
      val prefixLength = benchmarkRandom.nextInt(range.end - range.start)
      moveCaret(range.start + prefixLength)
      val lookup = getSuggestions(expectedLine, settings)
      emulator.pickBestSuggestion(expectedLine.substring(0, range.start - offset + prefixLength), lookup, this).also {
        LookupManager.hideActiveLookup(project)
        addLookup(it)
      }
    }
  }

  private fun Session.emulateCG(expectedLine: String, completableRanges: List<TextRange>, offset: Int, settings: CompletionGolfEmulation.Settings) {
    val emulator = CompletionGolfEmulation.createFromSettings(completionGolfSettings, expectedLine)
    var currentString = ""
    while (currentString != expectedLine) {
      val nextChar = expectedLine[currentString.length].toString()
      if (!completableRanges.any { offset + currentString.length >= it.start && offset + currentString.length < it.end }) {
        currentString += nextChar
        continue
      }

      moveCaret(offset + currentString.length)
      val lookup = getSuggestions(expectedLine, settings)

      emulator.pickBestSuggestion(currentString, lookup, this).also {
        if (settings.invokeOnEachChar) {
          LookupManager.hideActiveLookup(project)
        }
        currentString += it.selectedWithoutPrefix() ?: nextChar
        if (currentString.isNotEmpty() && !settings.invokeOnEachChar) {
          if (it.suggestions.isEmpty() || currentString.last().let { ch -> !(ch == '_' || ch.isLetter() || ch.isDigit()) }) {
            LookupManager.hideActiveLookup(project)
          }
        }
        addLookup(it)
      }
    }
  }

  private fun getSuggestions(expectedLine: String, settings: CompletionGolfEmulation.Settings): com.intellij.cce.core.Lookup {
    if (settings.isDefaultProvider()) {
      return callCompletion(expectedLine, null)
    }
    val lang = com.intellij.lang.Language.findLanguageByID(language.ideaLanguageId)
               ?: throw IllegalStateException("Can't find language \"${language.ideaLanguageId}\"")
    val provider = SuggestionsProvider.find(project, settings.suggestionsProvider)
                   ?: throw IllegalStateException("Can't find suggestions provider \"${settings.suggestionsProvider}\"")
    return provider.getSuggestions(expectedLine, editor!!, lang)
  }

  private fun positionToString(offset: Int): String {
    val logicalPosition = editor!!.offsetToLogicalPosition(offset)
    return "Offset: $offset, Line: ${logicalPosition.line}, Column: ${logicalPosition.column}."
  }

  private fun invokeCompletion(expectedText: String, prefix: String?): LookupEx? {
    val handlerFactory = CodeCompletionHandlerFactory.findCompletionHandlerFactory(project, language)
    val handler = handlerFactory?.createHandler(completionType, expectedText, prefix) ?: object : CodeCompletionHandlerBase(completionType,
                                                                                                                            false, false,
                                                                                                                            true) {
      // Guarantees synchronous execution
      override fun isTestingCompletionQualityMode() = true
      override fun lookupItemSelected(indicator: CompletionProgressIndicator?,
                                      item: LookupElement,
                                      completionChar: Char,
                                      items: MutableList<LookupElement>?) {
        afterItemInsertion(indicator, null)
      }
    }
    try {
      handler.invokeCompletion(project, editor)
    }
    catch (e: AssertionError) {
      LOG.warn("Completion invocation ended with error", e)
    }
    return LookupManager.getActiveLookup(editor)
  }

  private fun LookupImpl.finish(expectedItemIndex: Int, completionLength: Int): Boolean {
    selectedIndex = expectedItemIndex
    val document = editor.document
    val lengthBefore = document.textLength
    try {
      finishLookup(Lookup.AUTO_INSERT_SELECT_CHAR, items[expectedItemIndex])
    }
    catch (e: Throwable) {
      LOG.warn("Lookup finishing error.", e)
      return false
    }
    if (lengthBefore + completionLength != document.textLength) {
      LOG.info("Undo operation after finishing completion.")
      UndoManagerImpl.getInstance(project).undo(FileEditorManager.getInstance(project).selectedEditor)
      return false
    }
    return true
  }

  private fun hideLookup() = (LookupManager.getActiveLookup(editor) as? LookupImpl)?.hide()

  private fun LookupElement.asSuggestion(lookup: LookupImpl): Suggestion {
    val presentation = LookupElementPresentation()
    renderElement(presentation)
    val presentationText = "${presentation.itemText}${presentation.tailText ?: ""}" +
                           if (presentation.typeText != null) ": " + presentation.typeText else ""

    val insertedText = if (lookupString.contains('>')) lookupString.replace(Regex("<.+>"), "")
    else lookupString
    return Suggestion(insertedText,
                      presentationText,
                      sourceFromPresentation(presentation),
                      detailsFromFeatures(lookup, this)
    )
  }

  private fun sourceFromPresentation(presentation: LookupElementPresentation): SuggestionSource {
    val icon = presentation.icon
    val typeText = presentation.typeText

    return when {
      icon is CachedImageIcon && icon.originalPath == "/icons/codota-color-icon.png" -> SuggestionSource.CODOTA
      typeText == "@tab-nine" -> SuggestionSource.TAB_NINE
      typeText == "full-line" -> SuggestionSource.INTELLIJ
      else -> SuggestionSource.STANDARD
    }
  }

  private fun detailsFromFeatures(lookup: LookupImpl, element: LookupElement): Map<String, Any?> {
    val features = MLCompletionFeaturesUtil.getElementFeatures(lookup, element).features
    return mapOf(
      Suggestion.SCORE_KEY to features["ml_full_line_score"]?.toDoubleOrNull(),
      Suggestion.TOKENS_COUNT_KEY to features["ml_full_line_tokens_count"]?.toDoubleOrNull()
    )
  }

  private fun resetRandom(): Random = Random(completionGolfSettings?.randomSeed ?: 0)
}
