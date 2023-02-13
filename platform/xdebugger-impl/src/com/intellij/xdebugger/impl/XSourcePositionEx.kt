// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.xdebugger.XSourcePosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface XSourcePositionEx : XSourcePosition {
  /**
   * If this represents a position in a document loading/changing dynamically in an asynchronous way,
   * returning a non-empty flow will make the platform update the execution point highlighting
   * and scroll to the updated position on each new update emitted be the returned flow.
   */
  val positionUpdateFlow: Flow<NavigationMode>
    get() = emptyFlow()

  enum class NavigationMode {
    NONE, SCROLL, OPEN
  }
}