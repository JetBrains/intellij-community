// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress.suspender

import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus

/**
 * This interface defines a listener for task suspension events.
 * It provides methods that are called when a task is paused or resumed.
 * This listener is internal and meant to be used within the platform's progress suspender infrastructure.
 */
@ApiStatus.Internal
interface TaskSuspenderListener {

  /**
   * Called when a task is paused.
   * Implement this method to define the behavior that should occur when the task is suspended.
   */
  fun onPause(@NlsContexts.ProgressText reason: String?)

  /**
   * Called when a task is resumed.
   * Implement this method to define the behavior that should occur when the task is resumed.
   */
  fun onResume()
}