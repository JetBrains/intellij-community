package org.jetbrains.completion.full.line.platform.handlers

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.nextLeafs
import org.jetbrains.completion.full.line.AnalyzedFullLineProposal
import org.jetbrains.completion.full.line.language.FullLineLanguageSupporter
import org.jetbrains.completion.full.line.platform.FullLineLookupElement
import org.jetbrains.completion.full.line.services.TabSelectedItemStorage

class FirstTokenInsertHandler(private val supporter: FullLineLanguageSupporter) : InsertHandler<FullLineLookupElement> {

  override fun handleInsert(context: InsertionContext, item: FullLineLookupElement) {
    ApplicationManager.getApplication().runWriteAction {
      val document = context.document

      val startOffset = context.startOffset
      var completionRange = TextRange(startOffset + item.head.length, context.selectionEndOffset)
      if (item.suffix.isNotEmpty()) {
        context.document.insertString(context.selectionEndOffset, item.suffix)
        context.commitDocument()
        completionRange = completionRange.grown(item.suffix.length)
      }
      val selectionEndOffset = completionRange.endOffset

      val elementAtStart = context.file.findElementAt(completionRange.startOffset) ?: return@runWriteAction

      val nextToken = getNextToken(elementAtStart, item, completionRange).take(completionRange.length)
      val firstToken = item.head.removeSuffix(nextToken) + nextToken

      val completionWithoutToken =
        document.getText(TextRange(startOffset + firstToken.length, selectionEndOffset))
      document.replaceString(startOffset, selectionEndOffset, firstToken)

      val head = item.head
      if (completionWithoutToken.isNotEmpty()) {
        restartCompletion(
          context.project,
          context.editor,
          head + firstToken.removePrefix(head),
          item.proposal
        )
      }
    }
  }

  private fun getNextToken(
    element: PsiElement?,
    flElement: FullLineLookupElement,
    completionRange: TextRange
  ): String {
    val range = element?.textRange ?: return ""
    val realText = element.text.drop(completionRange.startOffset - range.startOffset)
    return when {
      supporter.isStringElement(element) -> {
        when {
          realText == element.text -> element.text + (element.nextSimpleOrEmpty(supporter)?.text ?: "")
          flElement.head.isEmpty() -> flElement.prefix + nextRawWord(realText.drop(flElement.prefix.length))
          else -> nextRawWord(realText)
        }
      }
      realText == flElement.prefix -> {
        val firstSimple = element.nextSimpleOrEmpty(supporter)
        val content = firstSimple?.nextNotEmpty()
        val lastSimple = content?.nextSimpleOrEmpty(supporter)
        realText + (firstSimple?.text ?: "") + (content?.text ?: "") + (lastSimple?.text ?: "")
      }
      else -> {
        val next = element.nextNotEmpty()
        val last = next?.nextSimpleOrEmpty(supporter)
        realText + (next?.text ?: "") + (last?.text ?: "")
      }
    }
  }

  private fun restartCompletion(project: Project, editor: Editor, head: String, proposal: AnalyzedFullLineProposal) {
    {
      if (!editor.isDisposed) {
        TabSelectedItemStorage.getInstance().saveTabSelected(head, proposal)
        CodeCompletionHandlerBase(CompletionType.BASIC, false, false, true)
          .invokeCompletion(project, editor)
      }
    }.let {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        ApplicationManager.getApplication().invokeAndWait(it)
      }
      else {
        ApplicationManager.getApplication().invokeLater(it)
      }
    }
  }

  private fun PsiElement.nextNotEmpty(): PsiElement? {
    return nextLeafs.firstOrNull { it.text.isNotEmpty() }
  }

  private fun PsiElement.nextSimpleOrEmpty(supporter: FullLineLanguageSupporter): PsiElement? {
    return nextLeafs.firstOrNull { it.text.isNotEmpty() }?.takeIf { supporter.isSimpleElement(it) }
  }

  private fun nextRawWord(text: String): String {
    if (text.isEmpty()) {
      return ""
    }
    var leadingChars = text.first().let { !(it.isLetterOrDigit() || it == '_') }
    return text.takeWhile {
      val isAccepted = it.isLetterOrDigit() || it == '_'
      if (isAccepted) {
        leadingChars = false
      }

      isAccepted || leadingChars
    }
  }
}
