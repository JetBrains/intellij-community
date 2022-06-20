// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.util.caching

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.kotlin.utils.addIfNotNull

abstract class FineGrainedEntityCache<Key: Any, Value: Any>(protected val project: Project, cleanOnLowMemory: Boolean): Disposable {
    private val cache: MutableMap<Key, Value> = HashMap()

    init {
        if (cleanOnLowMemory) {
            @Suppress("LeakingThis")
            LowMemoryWatcher.register(this::invalidate, this)
        }

        if (isFineGrainedCacheInvalidationEnabled) {
            @Suppress("LeakingThis")
            subscribe()
        }
    }

    override fun dispose() {
        invalidate()
    }

    fun get(key: Key): Value {
        try {
            checkValidity(key)
        } catch (e: Throwable) {
            synchronized(cache) {
                cache.remove(key)
            }

            throw e
        }

        if (!isFineGrainedCacheInvalidationEnabled) {
            return CachedValuesManager.getManager(project).getCachedValue(project) {
                val value = calculate(key)
                CachedValueProvider.Result.create(value, globalDependencies(key, value))
            }
        }

        // Fast check
        synchronized(cache) {
            val existingValue = cache[key]
            if (existingValue != null) {
                return existingValue
            }
        }

        ProgressManager.checkCanceled()

        val newValue = calculate(key)
        val extraCalculatedValues = extraCalculatedValues(key, newValue)

        ProgressManager.checkCanceled()

        synchronized(cache) {
            val existingValue = cache.putIfAbsent(key, newValue)

            extraCalculatedValues?.let {
                val iterator = it.entries.iterator()
                while (iterator.hasNext()) {
                    val (k, v) = iterator.next()
                    cache.putIfAbsent(k, v)
                }
            }

            existingValue?.let { return it }
        }

        return newValue
    }

    private fun invalidate() {
        synchronized(cache) {
            cache.clear()
        }
    }

    protected open fun invalidateKeys(keys: Collection<Key>, validityCondition: (Key) -> Boolean = { true }): Collection<Value> {
        val removedValues = mutableListOf<Value>()
        synchronized(cache) {
            for (key in keys) {
                removedValues.addIfNotNull(cache.remove(key))
            }
            checkKeys(validityCondition)
        }
        return removedValues
    }

    protected open fun invalidateKeys(condition: (Key) -> Boolean, validityCondition: (Key) -> Boolean = { true }): Collection<Value> {
        val removedValues = mutableListOf<Value>()
        synchronized(cache) {
            val iterator = cache.entries.iterator()
            while(iterator.hasNext()) {
                val (key, value )= iterator.next()
                if (condition(key)) {
                    removedValues += value
                    iterator.remove()
                }
            }
            checkKeys(validityCondition)
        }
        return removedValues
    }

    private fun checkKeys(validityCondition: (Key) -> Boolean) {
        for (key in cache.keys) {
            if (validityCondition(key)) {
                checkValidity(key)
            }
        }
    }

    protected abstract fun subscribe()

    protected abstract fun globalDependencies(key: Key, value: Value): List<Any>

    protected abstract fun checkValidity(key: Key)

    protected abstract fun calculate(key: Key): Value

    protected open fun extraCalculatedValues(key: Key, value: Value): Map<Key, Value>? = null

    companion object {
        val isFineGrainedCacheInvalidationEnabled: Boolean
            get() = Registry.`is`("kotlin.caches.fine.grained.invalidation")
    }
}