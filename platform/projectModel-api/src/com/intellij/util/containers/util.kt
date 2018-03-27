/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.util.containers

import com.intellij.util.SmartList
import com.intellij.util.lang.CompoundRuntimeException
import gnu.trove.THashSet
import java.util.*
import java.util.stream.Stream

fun <K, V> MutableMap<K, MutableList<V>>.remove(key: K, value: V) {
  val list = get(key)
  if (list != null && list.remove(value) && list.isEmpty()) {
    remove(key)
  }
}

fun <K, V> MutableMap<K, MutableList<V>>.putValue(key: K, value: V) {
  val list = get(key)
  if (list == null) {
    put(key, SmartList<V>(value))
  }
  else {
    list.add(value)
  }
}

fun Collection<*>?.isNullOrEmpty() = this == null || isEmpty()

inline fun <T, R> Iterator<T>.computeIfAny(processor: (T) -> R): R? {
  for (item in this) {
    val result = processor(item)
    if (result != null) {
      return result
    }
  }
  return null
}

inline fun <T, R> Array<T>.computeIfAny(processor: (T) -> R): R? {
  for (file in this) {
    val result = processor(file)
    if (result != null) {
      return result
    }
  }
  return null
}

inline fun <T, R> List<T>.computeIfAny(processor: (T) -> R): R? {
  for (item in this) {
    val result = processor(item)
    if (result != null) {
      return result
    }
  }
  return null
}

fun <T> List<T>?.nullize() = if (isNullOrEmpty()) null else this

inline fun <T> Array<out T>.forEachGuaranteed(operation: (T) -> Unit): Unit {
  var errors: MutableList<Throwable>? = null
  for (element in this) {
    try {
      operation(element)
    }
    catch (e: Throwable) {
      if (errors == null) {
        errors = SmartList()
      }
      errors.add(e)
    }
  }
  CompoundRuntimeException.throwIfNotEmpty(errors)
}

inline fun <T> Collection<T>.forEachGuaranteed(operation: (T) -> Unit): Unit {
  var errors: MutableList<Throwable>? = null
  for (element in this) {
    try {
      operation(element)
    }
    catch (e: Throwable) {
      if (errors == null) {
        errors = SmartList()
      }
      errors.add(e)
    }
  }
  CompoundRuntimeException.throwIfNotEmpty(errors)
}

fun <T> Array<T>?.stream(): Stream<T> = if (this != null) Stream.of(*this) else Stream.empty()

fun <T> Stream<T>?.isEmpty(): Boolean = this == null || !this.findAny().isPresent

fun <T> Stream<T>?.notNullize(): Stream<T> = this ?: Stream.empty()

fun <T> Stream<T>?.getIfSingle(): T? =
    this?.limit(2)
        ?.map { Optional.ofNullable(it) }
        ?.reduce(Optional.empty()) { a, b -> if (a.isPresent xor b.isPresent) b else Optional.empty() }
        ?.orElse(null)

/**
 * There probably could be some performance issues if there is lots of streams to concat. See
 * http://mail.openjdk.java.net/pipermail/lambda-dev/2013-July/010659.html for some details.
 *
 * Also see [Stream.concat] documentation for other possible issues of concatenating large number of streams.
 */
fun <T> concat(vararg streams: Stream<T>): Stream<T> = Stream.of(*streams).reduce(Stream.empty()) { a, b -> Stream.concat(a, b) }

inline fun MutableList<Throwable>.catch(runnable: () -> Unit) {
  try {
    runnable()
  }
  catch (e: Throwable) {
    add(e)
  }
}

inline fun <T, R> Array<out T>.mapSmart(transform: (T) -> R): List<R> {
  val size = size
  return when (size) {
    1 -> SmartList(transform(this[0]))
    0 -> SmartList()
    else -> mapTo(ArrayList(size), transform)
  }
}

inline fun <T, R> Collection<T>.mapSmart(transform: (T) -> R): List<R> {
  val size = size
  return when (size) {
    1 -> SmartList(transform(first()))
    0 -> emptyList()
    else -> mapTo(ArrayList(size), transform)
  }
}

/**
 * Not mutable set will be returned.
 */
inline fun <T, R> Collection<T>.mapSmartSet(transform: (T) -> R): Set<R> {
  val size = size
  return when (size) {
    1 -> {
      val result = SmartHashSet<R>()
      result.add(transform(first()))
      result
    }
    0 -> emptySet()
    else -> mapTo(THashSet(size), transform)
  }
}

inline fun <T, R : Any> Collection<T>.mapSmartNotNull(transform: (T) -> R?): List<R> {
  val size = size
  return if (size == 1) {
    transform(first())?.let { SmartList<R>(it) } ?: SmartList<R>()
  }
  else {
    mapNotNullTo(ArrayList<R>(size), transform)
  }
}

fun <T> List<T>.toMutableSmartList(): MutableList<T> {
  return when (size) {
    1 -> SmartList(first())
    0 -> SmartList()
    else -> ArrayList(this)
  }
}

inline fun <T> Collection<T>.filterSmart(predicate: (T) -> Boolean): List<T> {
  val result: MutableList<T> = when (size) {
    1 -> SmartList()
    0 -> return emptyList()
    else -> ArrayList()
  }
  filterTo(result, predicate)
  return result
}

inline fun <T> Collection<T>.filterSmartMutable(predicate: (T) -> Boolean): MutableList<T> {
  return filterTo(if (size <= 1) SmartList() else ArrayList(), predicate)
}