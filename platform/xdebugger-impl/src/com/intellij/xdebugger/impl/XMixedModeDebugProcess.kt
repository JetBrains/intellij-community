// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.xdebugger.frame.XSuspendContext

interface XMixedModeDebugProcess {
  suspend fun resumeAndWait(): Boolean
  fun getStoppedThreadId(context : XSuspendContext) : Long
}