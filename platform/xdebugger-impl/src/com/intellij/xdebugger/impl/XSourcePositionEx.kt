// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.xdebugger.XSourcePosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface XSourcePositionEx : XSourcePosition {
  /**
   * If this represents a position in a document the content of which is loaded/changed dynamically in an
   * asynchronous way, returning a non-empty flow will make the platform update the execution point
   * highlighting on each update emitted by the returned flow, optionally scrolling to the updated position
   * if the emitted value is `true`.
   */
  val positionUpdateFlow: Flow<Boolean>
    get() = emptyFlow()
}