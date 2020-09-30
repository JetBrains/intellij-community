// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework

import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.ThrowableRunnable
import org.jetbrains.annotations.TestOnly

/**
 * Consider using Kotlin coroutines and `com.intellij.openapi.application.AppUIExecutor.onUiThread().coroutineDispatchingContext()`
 * @see com.intellij.openapi.application.AppUIExecutor.onUiThread
 */
@TestOnly
inline fun <V> runInEdtAndGet(crossinline compute: () -> V): V {
  @Suppress("DEPRECATION", "RemoveExplicitTypeArguments")
  return EdtTestUtil.runInEdtAndGet(ThrowableComputable<V, Throwable> { compute() })
}

/**
 * Consider using Kotlin coroutines and `com.intellij.openapi.application.AppUIExecutor.onUiThread().coroutineDispatchingContext()`
 * @see com.intellij.openapi.application.AppUIExecutor.onUiThread
 */
@TestOnly
inline fun runInEdtAndWait(crossinline runnable: () -> Unit) {
  @Suppress("DEPRECATION", "RemoveExplicitTypeArguments")
  EdtTestUtil.runInEdtAndWait(ThrowableRunnable<Throwable> { runnable() })
}