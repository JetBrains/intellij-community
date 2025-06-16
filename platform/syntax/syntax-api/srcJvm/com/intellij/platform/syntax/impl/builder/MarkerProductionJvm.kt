// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.impl.builder

import fleet.util.multiplatform.Actual
import kotlin.math.min

/**
 * JVM implementation of [makeStackTraceRelative]
 */
@Suppress("unused")
@Actual("makeStackTraceRelative")
internal fun makeStackTraceRelativeJvm(th: Throwable, relativeTo: Throwable): Throwable {
  val trace = th.stackTrace
  val rootTrace = relativeTo.stackTrace
  var i = 0
  val len = min(trace.size, rootTrace.size)
  while (i < len) {
    if (trace[trace.size - i - 1] == rootTrace[rootTrace.size - i - 1]) {
      i++
      continue
    }
    val newDepth = trace.size - i
    th.setStackTrace(trace.copyOf<StackTraceElement?>(newDepth))
    break
  }
  return th
}
