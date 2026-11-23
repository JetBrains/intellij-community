// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal object JcefHwFacadeState {
  // updated once JBCefApp really initialized and has an instance
  val started: AtomicBoolean = AtomicBoolean(false)

  @JvmStatic
  fun isStarted(): Boolean = started.load()

  @JvmStatic
  fun setStarted(value: Boolean) {
    started.store(value)
  }
}