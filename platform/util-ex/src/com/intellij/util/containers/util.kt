// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.SmartList
import com.intellij.util.lang.CompoundRuntimeException
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

fun Collection<*>?.isNullOrEmpty(): Boolean = this == null || isEmpty()

val <T> List<T>.tail: List<T> get() = this.subList(1, this.size)

fun <T> List<T>.toHeadAndTail(): Pair<T, List<T>>? = if (this.isEmpty()) null else this.first() to this.tail

fun <T> List<T>?.nullize(): List<T>? = if (isNullOrEmpty()) null else this

inline fun <T> Array<out T>.forEachGuaranteed(operation: (T) -> Unit) {
  return iterator().forEachGuaranteed(operation)
}

inline fun <T> Collection<T>.forEachGuaranteed(operation: (T) -> Unit) {
  return iterator().forEachGuaranteed(operation)
}

inline fun <T> Iterator<T>.forEachGuaranteed(operation: (T) -> Unit) {
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

inline fun <T> Collection<T>.forEachLoggingErrors(logger: Logger, operation: (T) -> Unit) {
  return asSequence().forEachLoggingErrors(logger, operation)
}

inline fun <T> Sequence<T>.forEachLoggingErrors(logger: Logger, operation: (T) -> Unit) {
  forEach {
    try {
      operation(it)
    }
    catch (e: Throwable) {
      logger.error(e)
    }
  }
}

inline fun <T, R : Any> Collection<T>.mapNotNullLoggingErrors(logger: Logger, operation: (T) -> R?): List<R> {
  return mapNotNull {
    try {
      operation(it)
    }
    catch (e: Throwable) {
      logger.error(e)
      null
    }
  }
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
 * See also [Stream.concat] documentation for other possible issues of concatenating large number of streams.
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

fun <T> MutableList<T>.addIfNotNull(e: T?) {
  e?.let { add(it) }
}

inline fun <T, R> Array<out T>.mapSmart(transform: (T) -> R): List<R> {
  return when (val size = size) {
    1 -> SmartList(transform(this[0]))
    0 -> SmartList()
    else -> mapTo(ArrayList(size), transform)
  }
}

inline fun <T, reified R> Array<out T>.map2Array(transform: (T) -> R): Array<R> = Array(this.size) { i -> transform(this[i]) }

@Suppress("UNCHECKED_CAST")
inline fun <T, reified R> Collection<T>.map2Array(transform: (T) -> R): Array<R> = arrayOfNulls<R>(this.size).also { array ->
  this.forEachIndexed { index, t -> array[index] = transform(t) }
} as Array<R>

inline fun <T, R> Collection<T>.mapSmart(transform: (T) -> R): List<R> {
  return when (val size = size) {
    1 -> SmartList(transform(first()))
    0 -> emptyList()
    else -> mapTo(ArrayList(size), transform)
  }
}

/**
 * Not mutable set will be returned.
 */
inline fun <T, R> Collection<T>.mapSmartSet(transform: (T) -> R): Set<R> {
  return when (val size = size) {
    1 -> {
      Collections.singleton(transform(first()))
    }
    0 -> emptySet()
    else -> mapTo(HashSet(size), transform)
  }
}

inline fun <T, R : Any> Collection<T>.mapSmartNotNull(transform: (T) -> R?): List<R> {
  val size = size
  return if (size == 1) {
    transform(first())?.let { SmartList<R>(it) } ?: SmartList<R>()
  }
  else {
    mapNotNullTo(ArrayList(size), transform)
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

inline fun <reified E : Enum<E>, V> enumMapOf(): MutableMap<E, V> = EnumMap<E, V>(E::class.java)

fun <E> Collection<E>.toArray(empty: Array<E>): Array<E> {
  @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
  return (this as java.util.Collection<E>).toArray(empty)
}

/**
 * Given a collection of elements S returns collection of minimal elements M as ordered by [comparator].
 *
 * Let S = M ∪ R; M ∩ R = ∅. Then:
 * - ∀ m1 ∈ M, ∀ m2 ∈ M : m1 = m2;
 * - ∀ m ∈ M, ∀ r ∈ R : m < r.
 */
fun <T> Collection<T>.minimalElements(comparator: Comparator<in T>): Collection<T> {
  if (isEmpty() || size == 1) return this
  val result = SmartList<T>()
  for (item in this) {
    if (result.isEmpty()) {
      result.add(item)
    }
    else {
      val comparison = comparator.compare(result[0], item)
      if (comparison > 0) {
        result.clear()
        result.add(item)
      }
      else if (comparison == 0) {
        result.add(item)
      }
    }
  }
  return result
}

/**
 * _Example_
 * The following will print `1`, `2` and `3` when executed:
 * ```
 * arrayOf(1, 2, 3, 4, 5)
 *   .iterator()
 *   .stopAfter { it == 3 }
 *   .forEach(::println)
 * ```
 * @return an iterator, which stops [this] Iterator after first element for which [predicate] returns `true`
 */
inline fun <T> Iterator<T>.stopAfter(crossinline predicate: (T) -> Boolean): Iterator<T> = iterator {
  for (element in this@stopAfter) {
    yield(element)
    if (predicate(element)) {
      break
    }
  }
}