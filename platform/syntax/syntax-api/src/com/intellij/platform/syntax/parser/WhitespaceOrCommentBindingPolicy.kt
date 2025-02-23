// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.parser

import com.intellij.platform.syntax.SyntaxElementType
import org.jetbrains.annotations.ApiStatus

/**
 * Corresponds to [com.intellij.psi.tree.IElementType.isLeftBound]
 *
 * @see com.intellij.platform.syntax.impl.SyntaxTreeBuilderFactory.Builder.withWhitespaceOrCommentBindingPolicy
 */
@ApiStatus.Experimental
@ApiStatus.OverrideOnly
fun interface WhitespaceOrCommentBindingPolicy {
  fun isLeftBound(elementType: SyntaxElementType): Boolean
}