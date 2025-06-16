// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.impl.builder

import fleet.util.multiplatform.Actual

/**
 * Wasm implementation of [makeStackTraceRelative]
 */
@Suppress("unused")
@Actual("makeStackTraceRelative")
internal fun makeStackTraceRelativeWasmJs(th: Throwable, relativeTo: Throwable): Throwable {
  return th
}