// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract
import java.util.concurrent.ConcurrentMap

/**
 * A factory of various containers which implementations are different in pre/after jdk9.
 * Used to simplify porting jdk9+ collections to jdk8-modules
 */
@ApiStatus.Internal
abstract class Java11Shim {
  companion object {
    @JvmField
    var INSTANCE: Java11Shim = DefaultJava11Shim()
    fun <V> createConcurrentLongObjectMap(): ConcurrentLongObjectMap<V> = ConcurrentLongObjectHashMap()
    @JvmOverloads
    fun createConcurrentLongIntMap(defaultValue: Int, initialSize: Int = 16): ConcurrentLongIntMap = ConcurrentLongIntHashMap(defaultValue, initialSize)
    @JvmOverloads
    fun createConcurrentLongLongMap(defaultValue: Long, initialSize: Int = 16): ConcurrentLongLongMap =
      ConcurrentLongLongHashMap(defaultValue, initialSize)
    fun <V> createConcurrentIntObjectMap(): ConcurrentIntObjectMap<V> = ConcurrentIntObjectHashMap()
    fun <V> createConcurrentIntObjectMap(initialCapacity:Int, loadFactor:Float, concurrencyLevel:Int): ConcurrentIntObjectMap<V> = ConcurrentIntObjectHashMap(initialCapacity, loadFactor, concurrencyLevel)
    fun <V> createConcurrentIntObjectSoftValueMap(): ConcurrentIntObjectMap<V> = ConcurrentIntKeySoftValueHashMap()
    fun <V> createConcurrentIntObjectWeakValueMap(): ConcurrentIntObjectMap<V> = ConcurrentIntKeyWeakValueHashMap()

    /** Whether [className] is one of [classNames] or a class nested in one of them. */
    @JvmStatic
    fun isClassOrNestedClass(className: String, classNames: Array<out String>): Boolean {
      return classNames.any { name -> className.startsWith(name) && (className.length == name.length || className[name.length] == '$') }
    }

    /** The top [limit] of [frames] below their leading frames of [skippedClassNames] and of the classes nested in them. */
    @JvmStatic
    fun topFrames(frames: Array<StackTraceElement>, limit: Int, skippedClassNames: Array<out String>): Array<StackTraceElement> {
      val start = frames.indexOfFirst { !isClassOrNestedClass(it.className, skippedClassNames) }
      if (start < 0) {
        return emptyArray()
      }
      return frames.copyOfRange(start, if (limit >= frames.size - start) frames.size else start + limit)
    }
  }

  /**
   * The implementation of `copyOf` is allowed to not do copy - it can return the same map, read `copyOf` as `immutable`.
   */
  abstract fun <K : Any, V> copyOf(map: Map<K, V>): Map<K, V>

  abstract fun <K : Any, V> mapOf(k: K, v: V): Map<K, V>

  abstract fun <K : Any, V> mapOf(k: K, v: V, k2: K, v2: V): Map<K, V>

  abstract fun <K : Any, V> mapOf(): Map<K, V>

  abstract fun <E> copyOf(collection: Collection<E>): Set<E>

  abstract fun <E> copyOfList(collection: Collection<E>): List<E>

  abstract fun <E> listOf(): List<E>

  abstract fun <E> listOf(element: E): List<E>

  abstract fun <E> listOf(e1: E, e2: E): List<E>

  abstract fun <E> listOf(array: Array<E>, size: Int): List<E>

  abstract fun getCallerClass(stackFrameIndex: Int): Class<*>?

  /**
   * The top [limit] frames of the current stack below the frames of this shim and of [calleeClassNames]:
   * the stack of the code that called into those classes. A nested class of a callee class counts as that class.
   * A [StackTraceElement] holds no class, so a caller can keep the frames and keep no class loader.
   * When [walksOnlyRequestedFrames] is false, the call converts the whole stack.
   */
  abstract fun getStackTrace(limit: Int, vararg calleeClassNames: String): Array<StackTraceElement>

  /**
   * Whether [getStackTrace] reads only the frames that it returns. When it does not, it converts the whole stack,
   * so a caller on a hot path keeps a Throwable instead, see [com.intellij.util.StackTraceSnapshot.captureOnHotPath].
   */
  open val walksOnlyRequestedFrames: Boolean
    get() = false

  /** Whether a frame of [className] belongs to this shim, or to one of [calleeClassNames] or a class nested in it. */
  protected fun isSkippedFrame(className: String, calleeClassNames: Array<out String>): Boolean {
    return className == javaClass.name || isClassOrNestedClass(className, calleeClassNames)
  }

  @Contract(value = "_, -> new", pure = true)
  abstract fun <K, V> createConcurrentWeakMap(hashingStrategy: HashingStrategy<in K & Any> = HashingStrategy.canonical()): ConcurrentMap<K & Any, V & Any>
}
