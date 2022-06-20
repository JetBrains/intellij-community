// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.util.caching

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
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
            checkKeyValidity(key)
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
            cache[key]?.let { return it }
        }

        ProgressManager.checkCanceled()

        val newValue = calculate(key)

        ProgressManager.checkCanceled()

        synchronized(cache) {
            cache.putIfAbsent(key, newValue)?.let { return it }
        }

        return newValue
    }

    protected fun putAll(map: Map<Key, Value>) {
        for((key, value) in map) {
            checkKeyValidity(key)
            checkValueValidity(value)
        }
        synchronized(cache) {
            for((key, value) in map) {
                cache.putIfAbsent(key, value)
            }
        }
    }

    protected fun invalidate() {
        synchronized(cache) {
            cache.clear()
        }
    }

    protected fun invalidateKeysAndGetOutdatedValues(
        keys: Collection<Key>,
        validityCondition: (Key, Value) -> Boolean = { _, _ -> true }
    ): Collection<Value> {
        val removedValues = mutableListOf<Value>()
        synchronized(cache) {
            for (key in keys) {
                removedValues.addIfNotNull(cache.remove(key))
            }
            checkEntities(validityCondition)
        }
        return removedValues
    }

    protected fun invalidateKeys(
        keys: Collection<Key>,
        validityCondition: (Key, Value) -> Boolean = { _, _ -> true }
    ) {
        synchronized(cache) {
            for (key in keys) {
                cache.remove(key)
            }
            checkEntities(validityCondition)
        }
    }

    protected fun invalidateEntries(
        condition: (Key, Value) -> Boolean,
        validityCondition: (Key, Value) -> Boolean = { _, _ -> true }
    ) {
        synchronized(cache) {
            val iterator = cache.entries.iterator()
            while(iterator.hasNext()) {
                val (key, value )= iterator.next()
                if (condition(key, value)) {
                    iterator.remove()
                }
            }
            checkEntities(validityCondition)
        }
    }

    private fun checkEntities(validityCondition: (Key, Value) -> Boolean = { _, _ -> true }) {
        for (entry in cache) {
            if (validityCondition(entry.key, entry.value)) {
                checkKeyValidity(entry.key)
                checkValueValidity(entry.value)
            }
        }
    }

    protected abstract fun subscribe()

    protected open fun globalDependencies(key: Key, value: Value): List<Any> =
        listOf(ProjectRootModificationTracker.getInstance(project))

    protected abstract fun checkKeyValidity(key: Key)

    protected open fun checkValueValidity(value: Value) {
    }

    protected abstract fun calculate(key: Key): Value

    companion object {
        val isFineGrainedCacheInvalidationEnabled: Boolean
            get() = Registry.`is`("kotlin.caches.fine.grained.invalidation")
    }
}