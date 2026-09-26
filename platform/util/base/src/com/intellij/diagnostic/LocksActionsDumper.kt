// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.function.Supplier
import kotlin.concurrent.Volatile

@ApiStatus.Internal
object LocksActionsDumper {
  private const val LOCKS_ACTIONS_DUMP_HEADER: @NonNls String = "---------- Locks & Actions dump ----------"

  @Volatile
  private var LOCKS_ACTIONS_DUMPER: Supplier<String?>? = null

  fun dumpLocksAndActionsStateOrNull(): String? {
    return LOCKS_ACTIONS_DUMPER?.get()?.decorate()
  }

  private fun String.decorate(): String {
    return LOCKS_ACTIONS_DUMP_HEADER + "\n" + this
  }

  fun setLocksAndActionsDumper(dumpProvider: Supplier<String?>?) {
    LOCKS_ACTIONS_DUMPER = dumpProvider
  }

  fun removeLocksAndActionsDumper() {
    LOCKS_ACTIONS_DUMPER = null
  }
}