package org.jetbrains.completion.full.line.python.formatters

import com.intellij.psi.PsiComment
import com.intellij.psi.templateLanguages.OuterLanguageElement
import org.jetbrains.completion.full.line.language.formatters.CodeFormatterBase
import org.jetbrains.completion.full.line.language.formatters.PlainTextFormatter
import org.jetbrains.completion.full.line.language.formatters.SkippedElementsFormatter
import org.jetbrains.completion.full.line.python.formatters.elements.*

class PythonCodeFormatter : CodeFormatterBase() {
  override val elementFormatters = listOf(
    WhitespaceFormatter('⇥', '⇤'),
    SkippedElementsFormatter(PsiComment::class.java, OuterLanguageElement::class.java),
    NumericalFormatter(false),
    ImportFormatter(),
    ParenthesizedWithoutTuplesFormatter(this, '⇥', '⇤'),
    StringFormatter('⇥', '⇤'),
    ListLiteralFormatter(),
    ArgumentListFormatter(this, '⇥', '⇤'),
    SequenceFormatter(this, '⇥', '⇤'),
    ParameterListFormatter(),
    ConditionalFormatter('⇥', '⇤'),
    PlainTextFormatter()
  )
}
