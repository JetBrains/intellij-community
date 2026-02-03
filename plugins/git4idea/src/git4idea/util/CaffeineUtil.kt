// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.util

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Caffeine.newBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

internal object CaffeineUtil {
  fun withIoExecutor(): Caffeine<Any, Any> = newBuilder().executor(Dispatchers.IO.asExecutor())
}
