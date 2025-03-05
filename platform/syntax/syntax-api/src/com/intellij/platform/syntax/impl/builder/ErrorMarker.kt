// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.impl.builder

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.element.SyntaxTokenTypes
import org.jetbrains.annotations.Nls

internal class ErrorMarker(
  markerId: Int,
  builder: ParsingTreeBuilder,
) : ProductionMarker(markerId, builder) {

  private var errorMessage: @Nls String? = null

  override fun isErrorMarker(): Boolean = true

  override fun dispose() {
    super.dispose()
    errorMessage = null
  }

  override fun getErrorMessage(): @Nls String? {
    return errorMessage
  }

  fun setErrorMessage(value: @Nls String) {
    errorMessage = builder.errorInterner.intern(value)
  }

  override fun getEndOffset(): Int = getStartOffset()

  override fun getTokenType(): SyntaxElementType = SyntaxTokenTypes.ERROR_ELEMENT

  override fun getEndIndex(): Int = _startIndex

  override fun getLexemeIndex(done: Boolean): Int = _startIndex

  override fun setLexemeIndex(value: Int, done: Boolean) =
    if (done) throw UnsupportedOperationException() else _startIndex = value
}