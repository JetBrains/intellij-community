// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree

import com.intellij.xdebugger.impl.ui.tree.nodes.XCustomizableTextRenderer

/**
 * This interface can be used to configure [XCustomizableTextRenderer] used to present [com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl]
 * as a part of [com.intellij.xdebugger.frame.presentation.XValuePresentation].
 */
internal interface XRendererDecoratorPresentation {
  fun decorate(renderer: XCustomizableTextRenderer): XCustomizableTextRenderer
}
