// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.parser

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.element.SyntaxTokenTypes
import org.jetbrains.annotations.ApiStatus

/**
 * Controls whitespace balancing behavior of SyntaxTreeBuilder.
 * By default, empty composite elements (containing no children) are bounded to the right (previous) neighbor, forming the following tree:
 * ```
 *  [previous_element]
 *  [whitespace]
 *  [empty_element]
 *    &lt;empty&gt;
 *  [next_element]
 * ```
 *
 * Left-bound elements are bounded to the left (next) neighbor instead:
 * ```
 *  [previous_element]
 *  [empty_element]
 *    &lt;empty&gt;
 *  [whitespace]
 *  [next_element]
 * ```
 * @return `true` if empty elements of this type should be bound to the left.
 *
 * @see SyntaxTreeBuilderFactory.Builder.withWhitespaceOrCommentBindingPolicy
 */
@ApiStatus.Experimental
@ApiStatus.OverrideOnly
fun interface WhitespaceOrCommentBindingPolicy {
  fun isLeftBound(elementType: SyntaxElementType): Boolean
}

/**
 * A [WhitespaceOrCommentBindingPolicy], which makes the [SyntaxTokenTypes.ERROR_ELEMENT] type left-bound.
 */
@ApiStatus.Experimental
object DefaultWhitespaceBindingPolicy : WhitespaceOrCommentBindingPolicy {
  override fun isLeftBound(elementType: SyntaxElementType): Boolean =
    elementType == SyntaxTokenTypes.ERROR_ELEMENT
}
