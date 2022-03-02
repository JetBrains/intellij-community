// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.ThrowableRunnable
import org.jetbrains.annotations.TestOnly

/**
 * Consider using Kotlin coroutines and [Dispatchers.EDT][com.intellij.openapi.application.EDT].
 * @see com.intellij.openapi.application.AppUIExecutor.onUiThread
 */
@TestOnly
inline fun <V> runInEdtAndGet(crossinline compute: () -> V): V {
  @Suppress("DEPRECATION", "RemoveExplicitTypeArguments")
  return EdtTestUtil.runInEdtAndGet(ThrowableComputable<V, Throwable> { compute() })
}

/**
 * Consider using Kotlin coroutines and [Dispatchers.EDT][com.intellij.openapi.application.EDT].
 * @see com.intellij.openapi.application.AppUIExecutor.onUiThread
 */
@TestOnly
inline fun runInEdtAndWait(crossinline runnable: () -> Unit) {
  @Suppress("DEPRECATION", "RemoveExplicitTypeArguments")
  EdtTestUtil.runInEdtAndWait(ThrowableRunnable<Throwable> { runnable() })
}