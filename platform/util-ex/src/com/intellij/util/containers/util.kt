// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.SmartList
import com.intellij.util.lang.CompoundRuntimeException
import java.util.*
import java.util.stream.Stream
import kotlin.collections.ArrayDeque
import kotlin.collections.HashSet

fun <K, V> MutableMap<K, MutableList<V>>.remove(key: K, value: V) {
  val list = get(key)
  if (list != null && list.remove(value) && list.isEmpty()) {
    remove(key)
  }
}

fun <K, V> MutableMap<K, MutableList<V>>.putValue(key: K, value: V) {
  val list = get(key)
  if (list == null) {
    put(key, SmartList(value))
  }
  else {
    list.add(value)
  }
}

fun Collection<*>?.isNullOrEmpty(): Boolean = this == null || isEmpty()

@Deprecated("use tail()", ReplaceWith("tail()"), DeprecationLevel.ERROR)
val <T> List<T>.tail: List<T>
  get() = tail()

/**
 * @return all the elements of a non-empty list except the first one
 */
fun <T> List<T>.tail(): List<T> {
  require(isNotEmpty())
  return subList(1, size)
}

/**
 * @return all the elements of a non-empty list except the first one or empty list
 */
fun <T> List<T>.tailOrEmpty(): List<T> {
  if (isEmpty()) return emptyList()
  return subList(1, size)
}

/**
 * @return pair of the first element and the rest of a non-empty list
 */
fun <T> List<T>.headTail(): Pair<T, List<T>> = Pair(first(), tail())

/**
 * @return pair of the first element and the rest of a non-empty list, or `null` if a list is empty
 */
fun <T> List<T>.headTailOrNull(): Pair<T, List<T>>? = if (isEmpty()) null else headTail()

/**
 * @return all the elements of a non-empty list except the last one
 */
fun <T> List<T>.init(): List<T> {
  require(isNotEmpty())
  return subList(0, size - 1)
}

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
  asSequence().forEach {
    try {
      operation(it)
    }
    catch (e: Throwable) {
      logger.error(e)
    }
  }
  return
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

fun <T> Stream<T>?.getIfSingle(): T? {
  return this?.limit(2)
    ?.map { Optional.ofNullable(it) }
    ?.reduce(Optional.empty()) { a, b -> if (a.isPresent xor b.isPresent) b else Optional.empty() }
    ?.orNull()
}

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

fun <T> MutableList<T>.addAllIfNotNull(vararg elements: T?) {
  elements.forEach { e -> e?.let { add(it) } }
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
inline fun <T, reified R> Collection<T>.map2Array(transform: (T) -> R): Array<R> {
  return arrayOfNulls<R>(this.size).also { array ->
    this.forEachIndexed { index, t -> array[index] = transform(t) }
  } as Array<R>
}

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
    else -> mapTo(java.util.HashSet(size), transform)
  }
}

inline fun <T, R : Any> Collection<T>.mapSmartNotNull(transform: (T) -> R?): List<R> {
  val size = size
  return if (size == 1) {
    transform(first())?.let { SmartList(it) } ?: SmartList()
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

inline fun <reified E : Enum<E>, V> enumMapOf(): MutableMap<E, V> = EnumMap(E::class.java)

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
inline fun <T> Iterator<T>.stopAfter(crossinline predicate: (T) -> Boolean): Iterator<T> {
  return iterator {
    for (element in this@stopAfter) {
      yield(element)
      if (predicate(element)) {
        break
      }
    }
  }
}

fun <T> Optional<T>.orNull(): T? = orElse(null)

fun <T> Iterable<T>?.asJBIterable(): JBIterable<T> = JBIterable.from(this)

fun <T> Array<T>?.asJBIterable(): JBIterable<T> = if (this == null) JBIterable.empty() else JBIterable.of(*this)

/**
 * Modify the elements of the array without creating a new array
 *
 * @return the array itself
 */
fun <T> Array<T>.mapInPlace(transform: (T) -> T): Array<T> {
  for (i in this.indices) {
    this[i] = transform(this[i])
  }
  return this
}

/**
 * @return sequence of distinct nodes in breadth-first order
 */
fun <Node> generateRecursiveSequence(initialSequence: Sequence<Node>, children: (Node) -> Sequence<Node>): Sequence<Node> {
  return sequence {
    val initialIterator = initialSequence.iterator()
    if (!initialIterator.hasNext()) {
      return@sequence
    }
    val visited = HashSet<Node>()
    val sequences = ArrayDeque<Sequence<Node>>()
    sequences.addLast(initialIterator.asSequence())
    while (sequences.isNotEmpty()) {
      val currentSequence = sequences.removeFirst()
      for (node in currentSequence) {
        if (visited.add(node)) {
          yield(node)
          sequences.addLast(children(node))
        }
      }
    }
  }
}

/**
 * Returns a new sequence either of single given element, if it is not null, or empty sequence if the element is null.
 */
fun <T : Any> sequenceOfNotNull(element: T?): Sequence<T> = if (element == null) emptySequence() else sequenceOf(element)
