// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.xdebugger.impl.actions.DebuggerActionHandler
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ThreadsActionsProvider {
  val freezeThreadHandler: DebuggerActionHandler?
    get() = null
  val thawThreadHandler: DebuggerActionHandler?
    get() = null
  val freezeOtherThreadsHandler: DebuggerActionHandler?
    get() = null
  val thawAllThreadsHandler: DebuggerActionHandler?
    get() = null
}