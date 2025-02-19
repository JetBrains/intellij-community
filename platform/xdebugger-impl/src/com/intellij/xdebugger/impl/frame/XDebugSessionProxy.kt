// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.xdebugger.XDebugSession
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface XDebugSessionProxy {

  // TODO WeakReference<XDebugSession>?
  class Monolith(val session: XDebugSession) : XDebugSessionProxy
}