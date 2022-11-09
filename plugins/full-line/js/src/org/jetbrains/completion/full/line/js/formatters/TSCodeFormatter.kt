package org.jetbrains.completion.full.line.js.formatters

import com.intellij.psi.PsiComment
import org.jetbrains.completion.full.line.language.formatters.CodeFormatterBase
import org.jetbrains.completion.full.line.language.formatters.PlainTextFormatter
import org.jetbrains.completion.full.line.language.formatters.SkippedElementsFormatter

class TSCodeFormatter : CodeFormatterBase(
  SkippedElementsFormatter(PsiComment::class.java),
  LeadingSpaceFormatter(),
  PlainTextFormatter(),
)
