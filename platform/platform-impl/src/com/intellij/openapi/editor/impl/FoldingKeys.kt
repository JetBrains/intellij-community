// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object FoldingKeys {

  /**
   * This key is needed for rendering inline completion.
   * Please see [com.intellij.codeInsight.inline.completion.render.InlineCompletionTextRenderManager].
   *
   * The problem it solves. When we render multiline inline completion, sometimes, we have symbols on the right, and they need to
   * be shifted to the bottom. For that, we fold the real symbols and draw the same ones in right locations using inlays.
   * We fold the symbols right after the caret at its position with the empty placeholder. Then, if a user types a symbol,
   * the IJ platform moves the caret to the end of the current line, because the folding is empty and it doesn't have a caret position.
   * This key forces the platform to consider the folding region as an additional caret position.
   */
  @JvmField
  val ADDITIONAL_CARET_POSITION_FOR_EMPTY_PLACEHOLDER: Key<Boolean> =
    Key.create("folding.additional.caret.position.for.empty.placeholder")
}
