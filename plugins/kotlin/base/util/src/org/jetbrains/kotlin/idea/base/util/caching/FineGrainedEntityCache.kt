// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.util.caching

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

abstract class FineGrainedEntityCache<Key : Any, Value : Any>(
    protected val project: Project,
    private val cleanOnLowMemory: Boolean,
) : Disposable {
    private val invalidationStamp = InvalidationStamp()
    private val lastInvalidatedTimestampMs = AtomicLong(0)
    protected abstract val cache: MutableMap<Key, Value>
    protected val logger = Logger.getInstance(javaClass)
    protected val initialized = AtomicBoolean(false)

    /**
     * This function has to be called the last statement of ctor,
     * after all properties that could be used on subscription
     * and invalidation are already available.
     */
    protected fun initialize() {
        check(!initialized.getAndSet(true)) { "${this.javaClass.name} has to be initialized once." }
        if (cleanOnLowMemory) {
            registerLowMemoryWatcher()
        }

        subscribe()
    }

    private fun registerLowMemoryWatcher() {
        LowMemoryWatcher.register({ runReadAction {
            val nowMs = System.currentTimeMillis()
            val isInvalidationThrottled = invalidationThrottlingMs > 0
                    && nowMs < lastInvalidatedTimestampMs.get() + invalidationThrottlingMs
            if (isInvalidationThrottled) {
                logger.debug("invalidation throttled")
            } else {
                logger.debug("invalidated on low memory")
                invalidate()
                lastInvalidatedTimestampMs.getAndSet(nowMs)
            }
        } }, this)
    }

    protected fun checkIsInitialized() {
        check(initialized.get()) { "${this.javaClass.name} has to be initialized." }
    }

    protected abstract fun <T> useCache(block: (MutableMap<Key, Value>) -> T): T

    override fun dispose() {
        invalidate()
    }

    @RequiresReadLock
    abstract operator fun get(key: Key): Value

    @RequiresReadLock
    fun values(): Collection<Value> {
        ThreadingAssertions.softAssertReadAccess()
        return useCache { it.values }
    }

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

    protected fun invalidate(writeAccessRequired: Boolean = false) {
        if (writeAccessRequired) {
            ThreadingAssertions.assertWriteAccess()
        } else {
            ThreadingAssertions.softAssertReadAccess()
        }
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

    @RequiresWriteLock
    protected fun invalidateKeysAndGetOutdatedValues(
        keys: Collection<Key>,
        validityCondition: ((Key, Value) -> Boolean)? = CHECK_ALL
    ): Collection<Value> {
        ThreadingAssertions.assertWriteAccess()
        return useCache { cache ->
            doInvalidateKeysAndGetOutdatedValues(keys, cache).also {
                invalidationStamp.incInvalidation()
                checkEntities(cache, validityCondition)
            }
        }
    }

    protected open fun doInvalidateKeysAndGetOutdatedValues(keys: Collection<Key>, cache: MutableMap<Key, Value>): Collection<Value> {
        return buildList {
            for (key in keys) {
                cache.remove(key)?.let(::add)
            }
        }
    }

    @RequiresWriteLock
    protected fun invalidateKeys(
        keys: Collection<Key>,
        validityCondition: ((Key, Value) -> Boolean)? = CHECK_ALL
    ) {
        ThreadingAssertions.assertWriteAccess()
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
    @RequiresWriteLock
    protected fun invalidateEntries(
        condition: (Key, Value) -> Boolean,
        validityCondition: ((Key, Value) -> Boolean)? = CHECK_ALL
    ) {
        ThreadingAssertions.assertWriteAccess()
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
                        logger.error(e)
                    }
                } else {
                    allEntriesChecked = false
                }
            }

            additionalEntitiesCheck(cache)
            if (allEntriesChecked) {
                invalidationStamp.reset()
            }
        }
    }

    protected open fun additionalEntitiesCheck(cache: MutableMap<Key, Value>) = Unit

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

        val isValidityChecksEnabled: Boolean by lazy {
            Registry.`is`("kotlin.caches.fine.grained.entity.validation")
        }

        val invalidationThrottlingMs: Int by lazy {
            maxOf(Registry.intValue("kotlin.caches.fine.grained.throttling.timeout.ms"), 0)
        }
    }
}

abstract class SynchronizedFineGrainedEntityCache<Key : Any, Value : Any>(
    project: Project,
    doSelfInitialization: Boolean = true,
    cleanOnLowMemory: Boolean = false
) :
    FineGrainedEntityCache<Key, Value>(project, cleanOnLowMemory) {
  @Deprecated("Do not use directly", level = DeprecationLevel.ERROR)
  override val cache: MutableMap<Key, Value> = HashMap()

  private val lock = Any()

  init {
    if (!doSelfInitialization) {
      initialize()
    }
  }

  final override fun <T> useCache(block: (MutableMap<Key, Value>) -> T): T {
    checkIsInitialized()
    return synchronized(lock) {
      @Suppress("DEPRECATION_ERROR")
      cache.run(block)
    }
  }

  override fun get(key: Key): Value {
    ThreadingAssertions.softAssertReadAccess()
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

abstract class SynchronizedFineGrainedValueCache<Value : Any>(project: Project, doSelfInitialization: Boolean = true, cleanOnLowMemory: Boolean = false) :
    SynchronizedFineGrainedEntityCache<Unit, Value>(project, cleanOnLowMemory = cleanOnLowMemory) {
    @Deprecated("Do not use directly", level = DeprecationLevel.ERROR)
    override val cache: MutableMap<Unit, Value> = HashMap(1)

    init {
        if (!doSelfInitialization) {
            initialize()
        }
    }

    fun value(): Value = get(Unit)
    abstract fun calculate(): Value
    final override fun calculate(key: Unit): Value = calculate()
    override fun checkKeyValidity(key: Unit) = Unit
}

abstract class LockFreeFineGrainedEntityCache<Key : Any, Value : Any>(
    project: Project,
    doSelfInitialization: Boolean = true,
    cleanOnLowMemory: Boolean
) :
    FineGrainedEntityCache<Key, Value>(project, cleanOnLowMemory) {
  @Deprecated("Do not use directly", level = DeprecationLevel.ERROR)
  override val cache: MutableMap<Key, Value> = ConcurrentHashMap()

  init {
    if (!doSelfInitialization) {
      initialize()
    }
  }

  final override fun <T> useCache(block: (MutableMap<Key, Value>) -> T): T {
    checkIsInitialized()
    @Suppress("DEPRECATION_ERROR")
    return cache.run(block)
  }

  override fun get(key: Key): Value {
    ThreadingAssertions.softAssertReadAccess()
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

fun <T : WorkspaceEntity> EntityChange<T>.oldEntity() = when (this) {
    is EntityChange.Added -> null
    is EntityChange.Removed -> entity
    is EntityChange.Replaced -> oldEntity
}

fun <T : WorkspaceEntity> EntityChange<T>.newEntity() = when (this) {
    is EntityChange.Added -> newEntity
    is EntityChange.Removed -> null
    is EntityChange.Replaced -> newEntity
}