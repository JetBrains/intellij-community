package org.jetbrains.completion.full.line.language.formatters

import com.intellij.psi.PsiComment

class JavaCodeFormatter : CodeFormatterBase(
  SkippedElementsFormatter(PsiComment::class.java),
  PlainTextFormatter(),
)
