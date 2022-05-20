// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.base.util.caching

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

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

        ProgressManager.checkCanceled()

        synchronized(cache) {
            val existingValue = cache.putIfAbsent(key, newValue)
            if (existingValue != null) {
                return existingValue
            }
        }

        return newValue
    }

    private fun invalidate() {
        synchronized(cache) {
            cache.clear()
        }
    }

    protected fun invalidateKeys(keys: Collection<Key>) {
        synchronized(cache) {
            for (key in keys) {
                cache.remove(key)
            }
        }
    }

    protected abstract fun subscribe()

    protected abstract fun globalDependencies(key: Key, value: Value): List<Any>

    protected abstract fun checkValidity(key: Key)

    protected abstract fun calculate(key: Key): Value

    companion object {
        val isFineGrainedCacheInvalidationEnabled: Boolean
            get() = Registry.`is`("kotlin.caches.fine.grained.invalidation")
    }
}