// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.toolbar.floating

import java.util.concurrent.atomic.AtomicLong

/**
 * Describes executor with throttling.
 * @param delay (ms) is minimum delay between action executions.
 */
internal class ExecutorWithThrottling(private val delay: Int) {
  private val lastExecutionTime = AtomicLong()

  /**
   * Executes [action] if elapsed time since last execution is less than [delay].
   * @param action is action that should be delayed, if executions requested frequently then [delay] time.
   */
  fun executeOrSkip(action: () -> Unit) {
    val currentTime = System.currentTimeMillis()
    val lastTime = lastExecutionTime.getAndUpdate {
      if (it + delay < currentTime) currentTime else it
    }
    // currentTime can be same for several executeOrSkip calls.
    // So we should double-check it.
    if (lastTime + delay < currentTime) {
      action()
    }
  }
}