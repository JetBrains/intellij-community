// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.concurrency

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.UserDataHolder
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function

@ApiStatus.Experimental
class AsyncCache<K : UserDataHolder,T>(
  private val modificationTracker: Function<K, ModificationTracker>,
  private val eval: Function<K, T>
) {

  private val key: Key<AtomicReference<Stored<T>>> = Key.create("AsyncCache${System.identityHashCode(this)}")

  private data class Stored<T>(val promise: AsyncPromise<T>, val modCount: Long)

  private fun isCancelled(promise: AsyncPromise<*>): Boolean {
    if (promise.isCancelled) return true
    if (!promise.isRejected) return false
    try {
      promise.get()
    }
    catch (e: ProcessCanceledException) {
      return true
    }
    catch (e: ExecutionException) {
      if (e.cause is ProcessCanceledException)
        return true
    }
    catch (e: Exception) {
    }
    return false
  }

  private fun isActual(stored: Stored<T>?, modCount: Long): Boolean =
    stored != null && stored.modCount == modCount && !isCancelled(stored.promise)

  fun computeOrGet(dataHolder: K): AsyncPromise<T> {
    val modCount = modificationTracker.apply(dataHolder).modificationCount
    val ref = createOrGetRef(dataHolder)
    val stored = ref.get()
    if (isActual(stored, modCount)) return stored.promise

    val newStored = Stored(AsyncPromise<T>(), modCount)
    var curStored = stored
    while (!ref.compareAndSet(curStored, newStored)) {
      ProgressManager.checkCanceled()
      curStored = ref.get()
      if (isActual(curStored, modCount)) return curStored.promise
    }

    val promise = newStored.promise
    try {
      promise.setResult(eval.apply(dataHolder))
    }
    catch (e: Exception) {
      promise.setError(e)
      throw e
    }
    return promise
  }

  private fun createOrGetRef(dataHolder: UserDataHolder): AtomicReference<Stored<T>> {
    dataHolder.getUserData(key)?.let { return it }
    synchronized(key) {
      dataHolder.getUserData(key)?.let { return it }
      val atomicReference = AtomicReference<Stored<T>>()
      dataHolder.putUserData(key, atomicReference)
      return atomicReference
    }
  }

}