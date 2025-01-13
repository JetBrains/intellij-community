// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.nodes

import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueNode
import org.jetbrains.annotations.ApiStatus
import com.intellij.xdebugger.frame.XFullValueEvaluator

// TODO: split into separate interfaces may be?
@ApiStatus.Internal
interface XValueNodeEx : XValueNode {
  /**
   * Returns [XValue] which is represented by this [XValueNode]
   */
  fun getXValue(): XValue

  /**
   * Removes attached by [setFullValueEvaluator] [XFullValueEvaluator]
   */
  fun clearFullValueEvaluator()
}