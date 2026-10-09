// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Please, do not use this service. Use [DumbService.runWhenSmart] to schedule a runnable in smart mode.
 *
 * A scheduled runnable runs on the EDT after both conditions are true:
 * 1. No scanning is in progress, and the initial scanning of the project has finished.
 * 2. The project is not in dumb mode.
 *
 * The real implementation lives in the `intellij.platform.lang.impl.backend` module, because it tracks the indexing scanning.
 * The default implementation, [AlwaysSmartModeScheduler], serves a process without that module. It reports smart mode at all times.
 */
@Internal
interface SmartModeScheduler {
  fun canRunSmart(): Boolean

  fun runWhenSmart(runnable: Runnable)

  /**
   * @return a bit set of [SCANNING] and [DUMB]. Zero means smart mode.
   */
  fun getCurrentMode(): Int

  companion object {
    const val SCANNING: Int = 1
    const val DUMB: Int = 1.shl(1)
  }
}
