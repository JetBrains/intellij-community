// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.util.ui

import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes

private val snapshot = AtomicInteger(0)
private val reporting = AtomicBoolean(false)

@OptIn(DelicateCoroutinesApi::class)
private val reportingJob = GlobalScope.launch {
  while (true) {
    val oldCounter = snapshot.get()
    val oldReporting = reporting.get()
    delay(2.minutes)
    val newCounter = snapshot.get()
    val newReporting = reporting.get()
    if (newCounter == oldCounter && oldReporting && newReporting) {
      logger<UIUtil>().warn("Synchronous AWT Event dispatch takes too long. This may be a deadlock in tests caused by a pending background write action.\n" +
                            "Consider using `com.intellij.testFramework.PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue`, instead of your way of `UIUtil.dispatchAllInvocationEvents`" +
                            "which permits execution of background write actions")
    }
  }
}

internal fun reportTooLongDispatch(): AccessToken {
  snapshot.incrementAndGet()
  reporting.set(true)
  return object : AccessToken() {
    override fun finish() {
      reporting.set(false)
    }
  }
}