// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.impl.builder

import fleet.util.multiplatform.Actual
import kotlin.math.min

@Suppress("unused")
@Actual("doHeavyCheckImpl")
internal fun doHeavyCheckImplJvm(
  production: MarkerProduction,
  doneMarker: CompositeMarker,
  anchorBefore: CompositeMarker?,
) {
  production.doHeavyCheckImpl_(doneMarker, anchorBefore)
}

private fun MarkerProduction.doHeavyCheckImpl_(
  doneMarker: CompositeMarker,
  anchorBefore: CompositeMarker?,
) {
  val idx = indexOf(doneMarker)

  var endIdx = production.size
  if (anchorBefore != null) {
    endIdx = indexOf(anchorBefore)
    if (idx > endIdx) {
      logger.error("'Before' marker precedes this one.")
    }
  }

  for (i in endIdx - 1 downTo idx + 1) {
    val item = getStartMarkerAt(i)
    if (item is CompositeMarker) {
      val otherMarker = item
      if (!otherMarker.isDone) {
        val debugAllocThis = myOptionalData.getAllocationTrace(doneMarker)
        val currentTrace = Throwable()
        if (debugAllocThis != null) {
          makeStackTraceRelative(debugAllocThis, currentTrace).printStackTrace(System.err)
        }
        val debugAllocOther = myOptionalData.getAllocationTrace(otherMarker)
        if (debugAllocOther != null) {
          makeStackTraceRelative(debugAllocOther, currentTrace).printStackTrace(System.err)
        }
        logger.error("Another not done marker added after this one. Must be done before this.")
      }
    }
  }
}

private fun makeStackTraceRelative(th: Throwable, relativeTo: Throwable): Throwable {
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
