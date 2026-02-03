// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.nodes

import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import org.jetbrains.annotations.ApiStatus

/**
 * Marks interface that can operate with [SimpleTextAttributes].
 */
@ApiStatus.Internal
interface XCustomizableTextRenderer : XValuePresentation.XValueTextRenderer {
  /**
   * Appends [text] presented with [attributes].
   */
  fun renderRaw(@NlsSafe text: String, attributes: SimpleTextAttributes)
}
