package org.jetbrains.completion.full.line.kotlin.formatters

import com.intellij.psi.PsiComment
import org.jetbrains.completion.full.line.language.formatters.CodeFormatterBase
import org.jetbrains.completion.full.line.language.formatters.PlainTextFormatter
import org.jetbrains.completion.full.line.language.formatters.SkippedElementsFormatter

class KotlinCodeFormatter : CodeFormatterBase(
  SkippedElementsFormatter(PsiComment::class.java),
  PlainTextFormatter(),
)
