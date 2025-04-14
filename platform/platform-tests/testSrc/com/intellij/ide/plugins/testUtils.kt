// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.testFramework.LoggedErrorProcessor
import java.util.concurrent.atomic.AtomicReference


internal fun <R> runAndReturnWithLoggedError(body: () -> R): Pair<R, Throwable> {
  val result = AtomicReference<R>()
  val err = LoggedErrorProcessor.executeAndReturnLoggedError { result.set(body()) }
  return result.get() to err
}