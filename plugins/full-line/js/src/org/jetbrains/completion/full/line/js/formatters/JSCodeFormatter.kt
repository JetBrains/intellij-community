package org.jetbrains.completion.full.line.js.formatters

import org.jetbrains.completion.full.line.language.formatters.CodeFormatterBase
import org.jetbrains.completion.full.line.language.formatters.PlainTextFormatter

class JSCodeFormatter : CodeFormatterBase(
  LeadingSpaceFormatter(),
  PlainTextFormatter(),
)
