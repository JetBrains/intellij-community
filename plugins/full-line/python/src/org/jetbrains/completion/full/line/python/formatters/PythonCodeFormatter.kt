package org.jetbrains.completion.full.line.python.formatters

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.templateLanguages.OuterLanguageElement
import org.jetbrains.completion.full.line.language.formatters.CodeFormatterBase
import org.jetbrains.completion.full.line.language.formatters.PlainTextFormatter
import org.jetbrains.completion.full.line.language.formatters.SkippedElementsFormatter

class PythonCodeFormatter : CodeFormatterBase(
  WhitespaceFormatter(),
  SkippedElementsFormatter(PsiComment::class.java, OuterLanguageElement::class.java),
  NumericalFormatter(false),
  ImportFormatter(),
  ParenthesizedWithoutTuplesFormatter(),
  StringFormatter(),
  ListLiteralFormatter(),
  ArgumentListFormatter(),
  SequenceFormatter(),
  ParameterListFormatter(),
  PlainTextFormatter()
) {
  override fun formatFinalElement(element: PsiElement, range: TextRange): String {
    return super.formatFinalElement(element, range).replace(TABULATION, "\t")
  }
}
