// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.util.caching

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.util.registry.Registry
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import org.jetbrains.kotlin.caches.project.cacheByClassInvalidatingOnRootModifications
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

abstract class FineGrainedEntityCache<Key : Any, Value : Any>(protected val project: Project, cleanOnLowMemory: Boolean) : Disposable {
    private val invalidationStamp = InvalidationStamp()
    protected abstract val cache: MutableMap<Key, Value>
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

    protected abstract fun <T> useCache(block: (MutableMap<Key, Value>) -> T): T

    override fun dispose() {
        invalidate()
    }

    abstract operator fun get(key: Key): Value

    fun values(): Collection<Value> = useCache { it.values }

    protected fun checkEntitiesIfRequired(cache: MutableMap<Key, Value>) {
        if (isValidityChecksEnabled && invalidationStamp.isCheckRequired()) {
            checkEntities(cache, CHECK_ALL)
        }
    }

    protected fun checkKeyAndDisposeIllegalEntry(key: Key) {
        if (isValidityChecksEnabled) {
            try {
                checkKeyValidity(key)
            } catch (e: Throwable) {
                useCache { cache ->
                    disposeIllegalEntry(cache, key)
                }

                logger.error(e)
            }
        }
    }

    protected open fun disposeIllegalEntry(cache: MutableMap<Key, Value>, key: Key) {
        cache.remove(key)
    }

    protected fun putAll(map: Map<Key, Value>) {
        if (isValidityChecksEnabled) {
            for ((key, value) in map) {
                checkKeyValidity(key)
                checkValueValidity(value)
            }
        }

        useCache { cache ->
            for ((key, value) in map) {
                cache.putIfAbsent(key, value)
            }
        }
    }

    protected fun invalidate() {
        useCache { cache ->
            doInvalidate(cache)
        }
    }

    /**
     * perform [cache] invalidation under the lock
     */
    protected open fun doInvalidate(cache: MutableMap<Key, Value>) {
        cache.clear()
    }

    protected fun invalidateKeysAndGetOutdatedValues(
        keys: Collection<Key>,
        validityCondition: ((Key, Value) -> Boolean)? = CHECK_ALL
    ): Collection<Value> = useCache { cache ->
        doInvalidateKeysAndGetOutdatedValues(keys, cache).also {
            invalidationStamp.incInvalidation()
            checkEntities(cache, validityCondition)
        }
    }

    protected open fun doInvalidateKeysAndGetOutdatedValues(keys: Collection<Key>, cache: MutableMap<Key, Value>): Collection<Value> {
        return buildList {
            for (key in keys) {
                cache.remove(key)?.let(::add)
            }
        }
    }

    protected fun invalidateKeys(
        keys: Collection<Key>,
        validityCondition: ((Key, Value) -> Boolean)? = CHECK_ALL
    ) {
        useCache { cache ->
            for (key in keys) {
                cache.remove(key)
            }
            invalidationStamp.incInvalidation()
            checkEntities(cache, validityCondition)
        }
    }

    /**
     * @param condition is a condition to find entries those will be invalidated and removed from the cache
     * @param validityCondition is a condition to find entries those have to be checked for their validity, see [checkKeyValidity] and [checkValueValidity]
     */
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
            invalidationStamp.incInvalidation()
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
                        checkKeyConsistency(cache, entry.key)
                        checkValueValidity(entry.value)
                    } catch (e: Throwable) {
                        iterator.remove()
                        disposeEntry(cache, entry)
                        throw e
                    }
                } else {
                    allEntriesChecked = false
                }
            }

            if (additionalEntitiesCheck(cache) && allEntriesChecked) {
                invalidationStamp.reset()
            }
        }
    }

    protected open fun additionalEntitiesCheck(cache: MutableMap<Key, Value>): Boolean = true

    protected open fun disposeEntry(cache: MutableMap<Key, Value>, entry: MutableMap.MutableEntry<Key, Value>) = Unit

    protected abstract fun subscribe()

    protected abstract fun checkKeyValidity(key: Key)
    protected open fun checkKeyConsistency(cache: MutableMap<Key, Value>, key: Key) = checkKeyValidity(key)

    protected open fun checkValueValidity(value: Value) {}

    /***
     * In some cases it is not possible to validate all entries of the cache.
     * That could result to the state when there are some invalid entries after a partial cache invalidation.
     *
     * InvalidationStamp is to address this problem:
     *  - currentCount is incremented any time when a partial cache invalidation happens
     *  - count is equal to number of partial cache invalidation WHEN all cache entries have been validated
     *
     *  No checks are required when count is equal to currentCount.
     */
    private class InvalidationStamp {

        private var currentCount: Int = 0
        private var count: Int = 0

        fun isCheckRequired(): Boolean = currentCount > count

        fun incInvalidation() {
            currentCount++
        }

        fun reset() {
            count = currentCount
        }

    }

    companion object {

        val CHECK_ALL: (Any, Any) -> Boolean = { _, _ -> true }

        val isFineGrainedCacheInvalidationEnabled: Boolean by lazy {
            Registry.`is`("kotlin.caches.fine.grained.invalidation")
        }

        val isValidityChecksEnabled: Boolean by lazy {
            Registry.`is`("kotlin.caches.fine.grained.entity.validation")
        }
    }
}

abstract class SynchronizedFineGrainedEntityCache<Key : Any, Value : Any>(project: Project, cleanOnLowMemory: Boolean) :
    FineGrainedEntityCache<Key, Value>(project, cleanOnLowMemory) {
    @Deprecated("Do not use directly", level = DeprecationLevel.ERROR)
    override val cache: MutableMap<Key, Value> by StorageProvider(project, javaClass) { HashMap() }

    private val lock = Any()

    final override fun <T> useCache(block: (MutableMap<Key, Value>) -> T): T =
        synchronized(lock) {
            @Suppress("DEPRECATION_ERROR")
            cache.run(block)
        }

    override fun get(key: Key): Value {
        checkKeyAndDisposeIllegalEntry(key)

        useCache { cache ->
            checkEntitiesIfRequired(cache)

            cache[key]
        }?.let { return it }

        ProgressManager.checkCanceled()

        val newValue = calculate(key)

        if (isValidityChecksEnabled) {
            checkValueValidity(newValue)
        }

        useCache { cache ->
            cache.putIfAbsent(key, newValue)
        }?.let { return it }

        postProcessNewValue(key, newValue)

        return newValue
    }

    /**
     * it has to be a pure function w/o side effects as a value could be recalculated
     */
    abstract fun calculate(key: Key): Value

    /**
     * side effect function on a newly calculated value
     */
    open fun postProcessNewValue(key: Key, value: Value) {}
}

fun EntityStorage.findModuleByEntityWithHack(entity: ModuleEntity, project: Project) = entity.findModule(this) ?:
// TODO: workaround to bypass bug with new modules not present in storageAfter
entity.findModule(WorkspaceModel.getInstance(project).entityStorage.current)

fun EntityStorage.findLibraryByEntityWithHack(entity: LibraryEntity, project: Project) = entity.findLibraryBridge(this) ?:
// TODO: workaround to bypass bug with new modules not present in storageAfter
entity.findLibraryBridge(WorkspaceModel.getInstance(project).entityStorage.current)

fun EntityStorage.findModuleWithHack(entity: ModuleEntity, project: Project) = entity.findModule(this) ?:
// TODO: workaround to bypass bug with new modules not present in storageAfter
entity.findModule(WorkspaceModel.getInstance(project).entityStorage.current)

abstract class LockFreeFineGrainedEntityCache<Key : Any, Value : Any>(project: Project, cleanOnLowMemory: Boolean) :
    FineGrainedEntityCache<Key, Value>(project, cleanOnLowMemory) {
    @Deprecated("Do not use directly", level = DeprecationLevel.ERROR)
    override val cache: MutableMap<Key, Value> by StorageProvider(project, javaClass) { ConcurrentHashMap() }

    final override fun <T> useCache(block: (MutableMap<Key, Value>) -> T): T =
        @Suppress("DEPRECATION_ERROR")
        cache.run(block)

    override fun get(key: Key): Value {
        checkKeyAndDisposeIllegalEntry(key)

        useCache { cache ->
            checkEntitiesIfRequired(cache)

            cache[key]
        }?.let { return it }

        ProgressManager.checkCanceled()

        return useCache { cache ->
            calculate(cache, key)
        }
    }

    abstract fun calculate(cache: MutableMap<Key, Value>, key: Key): Value

}


class StorageProvider<Storage : Any>(
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

fun <T : Any> SynchronizedFineGrainedEntityCache<Unit, T>.get() = get(Unit)


fun <T : WorkspaceEntity> EntityChange<T>.oldEntity() =
    when (this) {
        is EntityChange.Added -> null
        is EntityChange.Removed -> entity
        is EntityChange.Replaced -> oldEntity
    }

fun <T : WorkspaceEntity> EntityChange<T>.newEntity() =
    when (this) {
        is EntityChange.Added -> newEntity
        is EntityChange.Removed -> null
        is EntityChange.Replaced -> newEntity
    }