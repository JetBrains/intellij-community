package org.jetbrains.completion.full.line.java.formatters

import com.intellij.psi.PsiComment
import org.jetbrains.completion.full.line.language.formatters.CodeFormatterBase
import org.jetbrains.completion.full.line.language.formatters.PlainTextFormatter
import org.jetbrains.completion.full.line.language.formatters.SkippedElementsFormatter

class JavaCodeFormatter : CodeFormatterBase(
  SkippedElementsFormatter(PsiComment::class.java),
  PlainTextFormatter(),
)
