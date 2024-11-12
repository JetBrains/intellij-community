// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.frame

interface XMixedModeHighLevelDebugProcess {
  fun getFramesMatcher(): MixedModeFramesBuilder
  suspend fun triggerBringingManagedThreadsToUnBlockedState()
  fun pauseMixedModeSession()
}