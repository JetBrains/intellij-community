package org.jetbrains.completion.full.line.platform.handlers

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.template.impl.LiveTemplateLookupElementImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import org.jetbrains.completion.full.line.language.FullLineLanguageSupporter
import org.jetbrains.completion.full.line.platform.FullLineLookupElement
import org.jetbrains.completion.full.line.platform.diagnostics.FullLinePart
import org.jetbrains.completion.full.line.platform.diagnostics.logger
import org.jetbrains.completion.full.line.settings.state.MLServerCompletionSettings

class FullLineInsertHandler private constructor(private val supporter: FullLineLanguageSupporter) :
  InsertHandler<FullLineLookupElement> {

  override fun handleInsert(context: InsertionContext, item: FullLineLookupElement) {
    ApplicationManager.getApplication().runWriteAction {
      val document = context.document
      val offset = context.tailOffset
      var completionRange = TextRange(context.startOffset + item.head.length, context.selectionEndOffset)

      if (item.suffix.isNotEmpty()) {
        context.document.insertString(context.selectionEndOffset, item.suffix)
        context.commitDocument()
        completionRange = completionRange.grown(item.suffix.length)
      }
      val endLine = document.getLineEndOffset(document.getLineNumber(offset))

      if (LOG.isDebugEnabled) {
        val starLine = document.getLineStartOffset(document.getLineNumber(offset))
        LOG.debug(
          "Full line with picked suggestion: `"
          + document.getText(starLine, context.startOffset)
          + "|${document.getText(context.startOffset, context.tailOffset)}|"
          + document.getText(context.tailOffset, endLine)
          + "`."
        )
      }

      val elementAtStart = context.file.findElementAt(completionRange.startOffset) ?: return@runWriteAction

      removeOverwritingChars(
        document.getText(TextRange.create(completionRange.startOffset, offset)),
        document.getText(TextRange.create(context.selectionEndOffset, endLine))
      ).takeIf { it > 0 }?.run {
        LOG.debug("Removing overwriting characters `${document.text.substring(offset, +offset)}`.")
        document.deleteString(offset, plus(offset))
      }

      if (
        MLServerCompletionSettings.getInstance().enableStringsWalking(supporter.language)
        && supporter.isStringWalkingEnabled(elementAtStart)
      ) {
        val template = supporter.createStringTemplate(context.file, TextRange(context.startOffset, completionRange.endOffset))
        if (template != null) {
          LOG.debug("Create string-walking template `${template.string}`.")
          LiveTemplateLookupElementImpl.startTemplate(context, template)
        }
      }

      ProgressManager.getInstance().executeNonCancelableSection {
        val importedElements = supporter.autoImportFix(context.file, context.editor, completionRange)
        if (LOG.isDebugEnabled && importedElements.isNotEmpty()) {
          val a = "Elements were imported:" + importedElements.joinToString("--\n\t--") {
            it.text
          }
          LOG.debug(a)
        }
      }
    }
  }

  private fun removeOverwritingChars(completion: String, line: String): Int {
    var amount = 0

    for (char in line) {
      var found = false

      for (charWithOffset in completion.drop(amount)) {
        if (!charWithOffset.isLetterOrWhitespace() && charWithOffset == char) {
          found = true
          break
        }
      }
      if (found) {
        amount++
      }
      else {
        break
      }
    }

    return amount
  }

  private fun Char.isLetterOrWhitespace(): Boolean {
    return isWhitespace() || isLetter()
  }

  companion object {
    private val LOG = logger<FullLineInsertHandler>(FullLinePart.PRE_PROCESSING)

    fun of(context: InsertionContext, supporter: FullLineLanguageSupporter): InsertHandler<FullLineLookupElement> {
      return when (context.completionChar) {
        '\t' -> FirstTokenInsertHandler(supporter)
        else -> FullLineInsertHandler(supporter)
      }
    }
  }
}

fun Document.getText(start: Int, end: Int): String {
  return text.substring(start, end)
}
