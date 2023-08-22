// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.testFramework.common.runAll
import com.intellij.util.ThrowableConsumer
import com.intellij.util.ThrowablePairConsumer
import com.intellij.util.lang.CompoundRuntimeException
import org.jetbrains.annotations.ApiStatus

@ApiStatus.ScheduledForRemoval
@Deprecated(
  "Moved to com.intellij.testFramework.RunAll",
  ReplaceWith("com.intellij.testFramework.RunAll.runAll(input, action)")
)
fun <K, V> runAll(input: Map<out K?, V?>, action: ThrowablePairConsumer<in K?, in V?, Throwable?>) {
  RunAll.runAll(input, action)
}

@ApiStatus.ScheduledForRemoval
@Deprecated(
  "Moved to com.intellij.testFramework.RunAll",
  ReplaceWith("com.intellij.testFramework.RunAll.runAll(input, action)")
)
fun <T> runAll(input: Collection<T>, action: ThrowableConsumer<in T, Throwable?>) {
  RunAll.runAll(input, action)
}

@ApiStatus.ScheduledForRemoval
@Deprecated(
  "Moved to com.intellij.testFramework.common",
  ReplaceWith("com.intellij.testFramework.common.runAll(*actions)"),
)
fun runAll(vararg actions: () -> Unit) {
  runAll(*actions)
}

@ApiStatus.ScheduledForRemoval
@Deprecated(
  "Moved to com.intellij.testFramework.common",
  ReplaceWith("actions.runAll()", "com.intellij.testFramework.common.runAll"),
)
fun runAll(actions: Sequence<() -> Unit>) {
  actions.runAll()
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use other runAll methods or com.intellij.testFramework.common.runAllCatching")
inline fun MutableList<Throwable>.catchAndStoreExceptions(executor: () -> Unit) {
  try {
    executor()
  }
  catch (e: CompoundRuntimeException) {
    addAll(e.exceptions)
  }
  catch (e: Throwable) {
    add(e)
  }
}
