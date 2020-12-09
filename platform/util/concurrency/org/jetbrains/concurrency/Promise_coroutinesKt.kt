// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.concurrency

import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.ApiStatus

@ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
@Deprecated("Left for ABI compatibility")
@Suppress("ClassName", "DeprecatedCallableAddReplaceWith")
object Promise_coroutinesKt {
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  @Deprecated("Use asDeferred() defined in org.jetbrains.concurrency.Promises")
  @JvmStatic
  fun <T> Promise<T>.asDeferred(): Deferred<T> = asDeferredInternal()

  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  @Deprecated("Use await() defined in org.jetbrains.concurrency.Promises")
  @JvmStatic
  suspend fun <T> Promise<T>.await(): T = awaitInternal()
}
