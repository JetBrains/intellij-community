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