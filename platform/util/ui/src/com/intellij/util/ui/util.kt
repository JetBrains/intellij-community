// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.util.ui

import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.minutes

internal fun reportTooLongDispatch(): AccessToken {
  @Suppress("OPT_IN_USAGE") val job = GlobalScope.launch {
    delay(2.minutes)
    logger<UIUtil>().warn("Synchronous AWT Event dispatch takes too long. This may be a deadlock in tests caused by a pending background write action.\n" +
                          "Consider using `com.intellij.testFramework.PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue`, instead of your way of `UIUtil.dispatchAllInvocationEvents`" +
                          "which permits execution of background write actions")
  }
  return object : AccessToken() {
    override fun finish() {
      job.cancel()
    }
  }
}