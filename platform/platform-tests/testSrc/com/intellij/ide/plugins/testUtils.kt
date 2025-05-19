// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.util.ThrowableRunnable
import java.util.concurrent.atomic.AtomicReference


internal fun <R> runAndReturnWithLoggedError(body: () -> R): Pair<R, Throwable> {
  val result = AtomicReference<R>()
  val err = LoggedErrorProcessor.executeAndReturnLoggedError { result.set(body()) }
  return result.get() to err
}

internal fun <R> runAndReturnWithLoggedErrors(body: () -> R): Pair<R, List<Throwable>> {
  val result = AtomicReference<R>()
  val errors = AtomicReference<ArrayList<Throwable>>(arrayListOf())
  LoggedErrorProcessor.executeWith<RuntimeException?>(object : LoggedErrorProcessor() {
    override fun processError(category: String, message: String, details: Array<String?>, t: Throwable?): MutableSet<Action?> {
      assert(t != null) { "Unexpected error without Throwable: $message"}
      errors.getAndUpdate { it.add(t!!); it }
      return Action.NONE
    }
  }, ThrowableRunnable { result.set(body()) })
  return result.get() to errors.get()
}