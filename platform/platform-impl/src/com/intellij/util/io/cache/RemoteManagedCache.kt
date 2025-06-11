// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.cache

import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface RemoteManagedCache<K, V>: ManagedCache<K, V> {
  /**
   * A flow of values that came from the remote cache speculatively (before the actual request)
   */
  val prefetchFlow: Flow<Map.Entry<K, V>>
}