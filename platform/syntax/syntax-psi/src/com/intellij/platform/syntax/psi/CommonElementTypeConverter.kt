// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi

import com.intellij.platform.syntax.element.SyntaxTokenTypes
import com.intellij.platform.syntax.util.runtime.DUMMY_BLOCK
import com.intellij.psi.DummyBlockType
import com.intellij.psi.TokenType

internal class CommonElementTypeConverterFactory : ElementTypeConverterFactory {
  override fun getElementTypeConverter(): ElementTypeConverter = commonConverter

  private val commonConverter: ElementTypeConverter = elementTypeConverterOf(
    SyntaxTokenTypes.ERROR_ELEMENT to TokenType.ERROR_ELEMENT,
    SyntaxTokenTypes.WHITE_SPACE to TokenType.WHITE_SPACE,
    SyntaxTokenTypes.BAD_CHARACTER to TokenType.BAD_CHARACTER,
    DUMMY_BLOCK to DummyBlockType.DUMMY_BLOCK
  )
}
