// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ExtraWhitespaces {
  @JvmStatic
  internal var whitespaces = TokenSet.EMPTY

  @JvmStatic
  fun registerExtraWhitespace(whitespace: IElementType) {
    whitespaces = TokenSet.orSet(whitespaces, TokenSet.create(whitespace))
  }
}