// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
package com.intellij.diagnostic

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.function.Supplier
import kotlin.concurrent.Volatile

@ApiStatus.Internal
object ProgressIndicatorDumper {
  private const val PROGRESS_INDICATOR_DUMP_HEADER: @NonNls String = "---------- ProgressIndicator dump ----------"

  @Volatile
  private var PROGRESS_INDICATOR_DUMPER: Supplier<String?>? = null

  fun dumpProgressIndicatorState(): String {
    return (PROGRESS_INDICATOR_DUMPER?.get() ?: "No progress indicator dump")
  }

  fun dumpProgressIndicatorStateOrNull(): String? {
    return PROGRESS_INDICATOR_DUMPER?.get()?.decorate()
  }

  private fun String.decorate(): String {
    return PROGRESS_INDICATOR_DUMP_HEADER + "\n" + this
  }

  fun setProgressIndicatorDumper(dumpProvider: Supplier<String?>?) {
    PROGRESS_INDICATOR_DUMPER = dumpProvider
  }

  fun removeProgressIndicatorDumper() {
    PROGRESS_INDICATOR_DUMPER = null
  }
}
