// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.function.Supplier
import kotlin.concurrent.Volatile

/**
 * Adds the scanning read actions to a thread dump.
 *
 * `ScanningCancellationMonitor` installs the supplier, and only when its registry key is on. The
 * section shows which scanning worker holds a read action, and whether that worker can observe the
 * cancellation of its own progress indicator.
 */
@ApiStatus.Internal
object ScanningWorkDumper {
  private const val SCANNING_WORK_DUMP_HEADER: @NonNls String = "---------- Scanning read actions dump ----------"

  @Volatile
  private var SCANNING_WORK_DUMPER: Supplier<String?>? = null

  fun dumpScanningWorkStateOrNull(): String? {
    return SCANNING_WORK_DUMPER?.get()?.decorate()
  }

  private fun String.decorate(): String {
    return SCANNING_WORK_DUMP_HEADER + "\n" + this
  }

  fun setScanningWorkDumper(dumpProvider: Supplier<String?>?) {
    SCANNING_WORK_DUMPER = dumpProvider
  }

  fun removeScanningWorkDumper() {
    SCANNING_WORK_DUMPER = null
  }
}
