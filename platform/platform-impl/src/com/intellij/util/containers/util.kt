/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.containers

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

fun List<*>?.isNullOrEmpty() = this == null || isEmpty()

inline fun <T, R> Iterator<T>.computeOrNull(processor: (T) -> R): R? {
  for (file in this) {
    val result = processor(file)
    if (result != null) {
      return result
    }
  }
  return null
}

inline fun <T, R> Array<T>.computeOrNull(processor: (T) -> R): R? {
  for (file in this) {
    val result = processor(file)
    if (result != null) {
      return result
    }
  }
  return null
}

inline fun <T, R> List<T>.computeOrNull(processor: (T) -> R): R? {
  for (file in this) {
    val result = processor(file)
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