// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.util

import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.throwIfNotAlive
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
fun Lifetime.onTerminationOrNow(action: () -> Unit) {
  val isAlive = onTerminationIfAlive {
    action()
  }

  if (!isAlive) {
    action()
    throwIfNotAlive()
  }
}

@ApiStatus.Experimental
fun Lifetime.onTerminationOrNowSafe(action: () -> Unit) {
  val isAlive = onTerminationIfAlive {
    action()
  }

  if (!isAlive) {
    action()
  }
}