package org.jetbrains.completion.full.line.language.formatters

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.completion.full.line.language.CodeFormatter
import org.jetbrains.completion.full.line.language.ElementFormatter

abstract class CodeFormatterBase(private vararg val elementFormatters: ElementFormatter) : CodeFormatter {
  private val filters = elementFormatters.map { it::filter }

  override fun format(element: PsiElement, range: TextRange, editor: Editor): String {
    TABULATION = getTabulation(editor)
    val code = StringBuilder()

    var skipUntil = 0
    SyntaxTraverser.psiTraverser()
      .withRoot(element)
      .onRange(range)
      .filter { el -> filters.mapNotNull { it(el) }.any { it } }
      .forEach lit@{
        if (it.textOffset < skipUntil) {
          return@lit
        }

        if (it.startOffset + it.text.length >= range.endOffset) {
          if (elementFormatters.filterIsInstance<SkippedElementsFormatter>()
              .none { formatter -> formatter.condition(it) }
          ) {
            code.append(formatFinalElement(it, range))
          }

          return code.toString()
        }

        var sk = false

        elementFormatters.forEach { elementFormatter ->
          if (!sk && elementFormatter.condition(it)) {
            code.append(elementFormatter.format(it))
            sk = true
          }
        }

        skipUntil = it.textRange.endOffset
      }
    return code.toString()
  }

  protected open fun formatFinalElement(element: PsiElement, range: TextRange): String {
    return element.text.slice(IntRange(0, (range.endOffset - element.startOffset - 1)))
  }

  private fun getTabulation(editor: Editor): String {
    val char = if (editor.settings.isUseTabCharacter(editor.project)) "\t" else " "
    return char.repeat(editor.settings.getTabSize(editor.project))
  }

  companion object {
    var TABULATION = "    "
  }
}
