package com.intellij.remoteDev.util

import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.throwIfNotAlive

fun Lifetime.onTerminationOrNow(action: () -> Unit) {
  val isAlive = onTerminationIfAlive {
    action()
  }

  if (!isAlive) {
    action()
    throwIfNotAlive()
  }
}