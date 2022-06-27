// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.util.caching

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.caches.project.cacheByClassInvalidatingOnRootModifications
import org.jetbrains.kotlin.utils.addIfNotNull
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

abstract class FineGrainedEntityCache<Key: Any, Value: Any>(protected val project: Project, cleanOnLowMemory: Boolean): Disposable {
    private val cache: MutableMap<Key, Value> by StorageProvider(project, javaClass) { HashMap() }
        @Deprecated("Do not use directly", level = DeprecationLevel.ERROR) get

    private var invalidationCount: Int = 0

    private var currentInvalidationCount: Int = 0

    private val lock = Any()

    protected val logger = Logger.getInstance(javaClass)

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

    private inline fun <T> useCache(block: (MutableMap<Key, Value>) -> T) {
        synchronized(lock) {
            @Suppress("DEPRECATION_ERROR")
            cache.run(block)
        }
    }

    override fun dispose() {
        invalidate()
    }

    fun get(key: Key): Value {
        if (isValidityChecksEnabled) {
            try {
                checkKeyValidity(key)
            } catch (e: Throwable) {
                useCache { cache ->
                    cache.remove(key)
                }

                logger.error(e)
            }
        }

        useCache { cache ->
            if (currentInvalidationCount > invalidationCount) {
                checkEntities(cache, CHECK_ALL)
            }

            cache[key]?.let { return it }
        }

        ProgressManager.checkCanceled()

        val newValue = calculate(key)

        if (isValidityChecksEnabled) {
            checkValueValidity(newValue)
        }

        ProgressManager.checkCanceled()

        useCache { cache ->
            cache.putIfAbsent(key, newValue)?.let { return it }
        }

        return newValue
    }

    protected fun putAll(map: Map<Key, Value>) {
        if (isValidityChecksEnabled) {
            for((key, value) in map) {
                checkKeyValidity(key)
                checkValueValidity(value)
            }
        }

        useCache { cache ->
            for((key, value) in map) {
                cache.putIfAbsent(key, value)
            }
        }
    }

    protected fun invalidate() {
        useCache { cache ->
            cache.clear()
        }
    }

    protected fun invalidateKeysAndGetOutdatedValues(
        keys: Collection<Key>,
        validityCondition: ((Key, Value) -> Boolean)? = CHECK_ALL
    ): Collection<Value> {
        val removedValues = mutableListOf<Value>()
        useCache { cache ->
            for (key in keys) {
                removedValues.addIfNotNull(cache.remove(key))
            }
            currentInvalidationCount++
            checkEntities(cache, validityCondition)
        }
        return removedValues
    }

    protected fun invalidateKeys(
        keys: Collection<Key>,
        validityCondition: ((Key, Value) -> Boolean)? = CHECK_ALL
    ) {
        useCache { cache ->
            for (key in keys) {
                cache.remove(key)
            }
            currentInvalidationCount++
            checkEntities(cache, validityCondition)
        }
    }

    protected fun invalidateEntries(
        condition: (Key, Value) -> Boolean,
        validityCondition: ((Key, Value) -> Boolean)? = CHECK_ALL
    ) {
        useCache { cache ->
            val iterator = cache.entries.iterator()
            while (iterator.hasNext()) {
                val (key, value) = iterator.next()
                if (condition(key, value)) {
                    iterator.remove()
                }
            }
            currentInvalidationCount++
            checkEntities(cache, validityCondition)
        }
    }

    private fun checkEntities(cache: MutableMap<Key, Value>, validityCondition: ((Key, Value) -> Boolean)?) {
        if (isValidityChecksEnabled && validityCondition != null) {
            var allEntriesChecked = true
            val iterator = cache.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (validityCondition(entry.key, entry.value)) {
                    try {
                        checkKeyValidity(entry.key)
                        checkValueValidity(entry.value)
                    } catch (e: Throwable) {
                        iterator.remove()
                        throw e
                    }
                } else {
                    allEntriesChecked = false
                }
            }
            if (allEntriesChecked) {
                invalidationCount = currentInvalidationCount
            }
        }
    }

    protected abstract fun subscribe()

    protected abstract fun checkKeyValidity(key: Key)

    protected open fun checkValueValidity(value: Value) {}

    protected abstract fun calculate(key: Key): Value

    companion object {

        val CHECK_ALL:(Any, Any) -> Boolean = { _, _ -> true }

        val isFineGrainedCacheInvalidationEnabled: Boolean by lazy {
            Registry.`is`("kotlin.caches.fine.grained.invalidation")
        }

        val isValidityChecksEnabled: Boolean by lazy {
            Registry.`is`("kotlin.caches.fine.grained.entity.validation")
        }
    }
}

private class StorageProvider<Storage: Any>(
    private val project: Project,
    private val key: Class<*>,
    private val factory: () -> Storage
) : ReadOnlyProperty<Any, Storage> {
    private val storage = lazy(factory)

    override fun getValue(thisRef: Any, property: KProperty<*>): Storage {
        if (!FineGrainedEntityCache.isFineGrainedCacheInvalidationEnabled) {
            @Suppress("DEPRECATION")
            return project.cacheByClassInvalidatingOnRootModifications(key) { factory() }
        }

        return storage.value
    }
}

fun <T: Any> FineGrainedEntityCache<Unit, T>.get() = get(Unit)