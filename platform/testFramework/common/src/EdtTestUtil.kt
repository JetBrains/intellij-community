// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.ThrowableRunnable
import org.jetbrains.annotations.TestOnly

/**
 * Consider using Kotlin coroutines and [Dispatchers.EDT][com.intellij.openapi.application.EDT].
 */
@TestOnly
fun <V> runInEdtAndGet(compute: () -> V): V {
  @Suppress("DEPRECATION", "RemoveExplicitTypeArguments")
  return EdtTestUtil.runInEdtAndGet(ThrowableComputable<V, Throwable> { compute() }, true)
}

/**
 * Consider using Kotlin coroutines and [Dispatchers.EDT][com.intellij.openapi.application.EDT].
 */
@TestOnly
fun <V> runInEdtAndGet(writeIntent: Boolean, compute: () -> V): V {
  @Suppress("DEPRECATION", "RemoveExplicitTypeArguments")
  return EdtTestUtil.runInEdtAndGet(ThrowableComputable<V, Throwable> { compute() }, writeIntent)
}

/**
 * Consider using Kotlin coroutines and [Dispatchers.EDT][com.intellij.openapi.application.EDT].
 */
@TestOnly
fun runInEdtAndWait(runnable: () -> Unit) {
  @Suppress("DEPRECATION", "RemoveExplicitTypeArguments")
  EdtTestUtil.runInEdtAndWait(ThrowableRunnable<Throwable> { runnable() }, true)
}

/**
 * Consider using Kotlin coroutines and [Dispatchers.EDT][com.intellij.openapi.application.EDT].
 */
@TestOnly
fun runInEdtAndWait(writeIntent: Boolean, runnable: () -> Unit) {
  @Suppress("DEPRECATION", "RemoveExplicitTypeArguments")
  EdtTestUtil.runInEdtAndWait(ThrowableRunnable<Throwable> { runnable() }, writeIntent)
}