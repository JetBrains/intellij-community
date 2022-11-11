package org.jetbrains.completion.full.line.language.formatters

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.completion.full.line.language.CodeFormatter
import org.jetbrains.completion.full.line.language.ElementFormatter
import kotlin.reflect.KClass

abstract class CodeFormatterBase : CodeFormatter {
  abstract val elementFormatters: List<ElementFormatter>

  val text = StringBuilder()

  private val filters by lazy { elementFormatters.map { it::filter } }

  override fun format(element: PsiElement, range: TextRange, editor: Editor): String {
    TABULATION = getTabulation(editor)

    var skipUntil = 0
    SyntaxTraverser.psiTraverser()
      .withRoot(element)
      .onRange(range)
      .filter { el -> filters.mapNotNull { it(el) }.any { it } }
      .forEach lit@{
        if (it.textOffset < skipUntil) return@lit

        if (it.startOffset + it.text.length >= range.endOffset) {
          finalFormat(it, range, ::formatFinalElement)
          for (formatter in elementFormatters) {
            formatter.resetState()
          }
          return text.toString().also {
            text.clear()
          }
        }

        formatAndStore(it)
        skipUntil = it.textRange.endOffset
      }
    for (formatter in elementFormatters) {
      formatter.resetState()
    }
    return text.toString().also {
      text.clear()
    }
  }

  protected open fun formatFinalElement(element: PsiElement, range: TextRange): String {
    return elementFormatters.firstOrNull { it.condition(element) }?.formatFinalElement(element, range)
           ?: element.text.slice(IntRange(0, (range.endOffset - element.startOffset - 1)))
  }

  private fun getTabulation(editor: Editor): String {
    val char = if (editor.settings.isUseTabCharacter(editor.project)) "\t" else " "
    return char.repeat(editor.settings.getTabSize(editor.project))
  }

  private fun formatAndStore(element: PsiElement) {
    text.append(format(element))
  }

  private fun finalFormat(element: PsiElement, range: TextRange, formatter: (PsiElement, TextRange) -> String) {
    if (elementFormatters.filterIsInstance<SkippedElementsFormatter>().none { it.condition(element) }) {
      text.append(formatter(element, range))
    }
  }

  fun format(element: PsiElement): String {
    return elementFormatters.firstOrNull { it.condition(element) }?.format(element) ?: ""
  }

  fun formatterOrNull(element: PsiElement, vararg skipFormatters: KClass<*>): ElementFormatter? {
    return elementFormatters.filter { !skipFormatters.contains(it::class) }
             .firstOrNull { it.condition(element) }
  }

  fun formatAll(element: PsiElement, range: TextRange): String {
    val text = StringBuilder()
    var skipUntil = 0
    SyntaxTraverser.psiTraverser()
      .withRoot(element)
      .onRange(range)
      .filter { el -> elementFormatters.map { it::filter }.mapNotNull { it(el) }.any { it } }
      .forEach lit@{
        if (it.textOffset < skipUntil) return@lit

        //if (it.startOffset + it.text.length >= range.endOffset) {
        //    finalFormat(it, range)
        //    return text.toString()
        //}

        text.append(format(it))
        skipUntil = it.textRange.endOffset
      }
    return text.toString()
  }

  companion object {
    var TABULATION = "    "
  }
}
