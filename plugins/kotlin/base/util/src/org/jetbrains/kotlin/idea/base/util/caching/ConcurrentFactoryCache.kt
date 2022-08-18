// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.util.caching

import com.intellij.openapi.util.RecursionManager
import com.intellij.util.ConcurrencyUtil
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentMap

/**
 * Variation of `ConcurrentFactoryMap` that allows passing the factory directly to the `get()` method.
 */
@ApiStatus.Internal
class ConcurrentFactoryCache<Key: Any, Value: Any>(private val storage: ConcurrentMap<Key, Value>) {
    fun get(key: Key, factory: (Key) -> Value): Value {
        val storage = this.storage
        var value = storage[key]
        if (value == null) {
            val stamp = RecursionManager.markStack()
            value = factory(key)
            if (stamp.mayCacheNow()) {
                value = ConcurrencyUtil.cacheOrGet(storage, key, value)
            }
        }
        return value
    }

    operator fun contains(key: Key): Boolean {
        return storage.containsKey(key)
    }

    fun remove(key: Key): Value? {
        return storage.remove(key)
    }

    fun clear() {
        storage.clear()
    }
}