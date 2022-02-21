// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.caches.project

import com.intellij.openapi.util.*
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProfiler
import com.intellij.psi.util.CachedValueProfiler.ValueTracker
import com.intellij.psi.util.CachedValueProvider
import com.intellij.util.*
import com.intellij.util.containers.NotNullList

class StrongCachedValue<T>(private val provider: CachedValueProvider<T>, private val trackValue: Boolean = false): CachedValue<T> {

    @Volatile
    private var data: Data<T>? = null

    private fun doCompute(): CachedValueProvider.Result<T>? {
        return provider.compute()
    }

    override fun getValueProvider(): CachedValueProvider<T> = provider

    private fun computeData(doCompute: Computable<CachedValueProvider.Result<T>>): Data<T> {
        var result: CachedValueProvider.Result<T>?
        var tracker: ValueTracker?
        if (CachedValueProfiler.isProfiling()) {
            CachedValueProfiler.newFrame().use { frame ->
                val compute = doCompute.compute()
                result = compute
                tracker = frame.newValueTracker(compute)
            }
        } else {
            result = doCompute.compute()
            tracker = null
        }
        if (result == null) {
            return Data(null, EMPTY_ARRAY, ArrayUtil.EMPTY_LONG_ARRAY, null)
        }
        val value = result!!.value
        val inferredDependencies = normalizeDependencies(result!!)
        val inferredTimeStamps = LongArray(inferredDependencies.size)
        for (i in inferredDependencies.indices) {
            inferredTimeStamps[i] = getTimeStamp(inferredDependencies[i])
        }
        return Data(value, inferredDependencies, inferredTimeStamps, tracker)
    }

    @Synchronized
    private fun cacheOrGetData(expected: Data<T>?, updatedValue: Data<T>?): Data<T>? {
        if (expected != getRawData()) return null
        if (updatedValue != null) {
            setData(updatedValue)
            return updatedValue
        }
        return expected
    }

    @Synchronized
    private fun setData(data: Data<T>?) {
        this.data = data
    }

    private fun normalizeDependencies(result: CachedValueProvider.Result<T>): Array<ModificationTracker> {
        val items = result.dependencyItems
        val value = result.value
        val rawDependencies = if (trackValue && value != null) ArrayUtil.append(items, value) else items
        val flattened: MutableList<ModificationTracker> = NotNullList(rawDependencies.size)
        collectDependencies(flattened, rawDependencies)
        return flattened.toTypedArray()
    }

    fun clear() {
        setData(null)
    }

    override fun hasUpToDateValue(): Boolean {
        return upToDateOrNull != null
    }

    override fun getUpToDateOrNull(): Data<T>? = getRawData()?.takeIf { checkUpToDate(it) }

    private fun checkUpToDate(data: Data<T>): Boolean {
        if (isUpToDate(data)) {
            return true
        }
        data.trackingInfo?.onValueInvalidated()
        return false
    }

    private fun getRawData(): Data<T>? = data

    private fun isUpToDate(data: Data<T>): Boolean {
        for (i in data.dependencies.indices) {
            val dependency = data.dependencies[i]
            if (isDependencyOutOfDate(dependency, data.timeStamps[i])) return false
        }
        return true
    }

    private fun isDependencyOutOfDate(dependency: ModificationTracker, oldTimeStamp: Long): Boolean {
        val timeStamp = getTimeStamp(dependency)
        return timeStamp < 0 || timeStamp != oldTimeStamp
    }

    private fun collectDependencies(resultingDeps: MutableList<ModificationTracker>, dependencies: Array<Any>) {
        for (dependency in dependencies) {
            if (dependency === ObjectUtils.NULL) continue
            resultingDeps.add(dependency as ModificationTracker)
        }
    }

    private fun getTimeStamp(dependency: ModificationTracker): Long = dependency.modificationCount

    fun setValue(result: CachedValueProvider.Result<T>): T? {
        val data = computeData { result }
        setData(data)
        return data.value
    }

    override fun getValue(): T = getValueWithLock()

    private fun getValueWithLock(): T {
        upToDateOrNull?.let { data ->
            if (IdempotenceChecker.areRandomChecksEnabled()) {
                IdempotenceChecker.applyForRandomCheck(
                    data, valueProvider
                ) {
                    computeData(Computable {
                        doCompute()
                    })
                }
            }
            return data.value!!
        }
        val stamp = RecursionManager.markStack()
        val calcData = Computable {
            computeData(Computable {
                doCompute()
            })
        }
        var data: Data<T>? = RecursionManager.doPreventingRecursion(this, true, calcData)
        if (data == null) {
            data = calcData.compute()
        } else if (stamp.mayCacheNow()) {
            while (true) {
                val alreadyComputed: Data<T>? = getRawData()
                val reuse = alreadyComputed != null && checkUpToDate(alreadyComputed)
                if (reuse) {
                    IdempotenceChecker.checkEquivalence(alreadyComputed, data, valueProvider.javaClass, calcData)
                }
                val toReturn: Data<T>? = cacheOrGetData(alreadyComputed, if (reuse) null else data)
                if (toReturn != null) {
                    if (data != toReturn && data.trackingInfo != null) {
                        data.trackingInfo!!.onValueRejected()
                    }
                    return toReturn.value!!
                }
            }
        }
        return data!!.value!!
    }

    class Data<T> internal constructor(
        private val innerValue: T?, val dependencies: Array<ModificationTracker>, val timeStamps: LongArray,
        val trackingInfo: ValueTracker?
    ) : Getter<T> {

        override fun get(): T? {
            return innerValue
        }

        val value: T?
            get() {
                trackingInfo?.onValueUsed()
                return innerValue
            }
    }

    companion object {
        private val EMPTY_ARRAY: Array<ModificationTracker> = emptyArray()
    }
}