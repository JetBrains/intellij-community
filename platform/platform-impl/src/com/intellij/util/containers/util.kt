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

fun <K, V> MutableMap<K, MutableList<V>>.remove(key: K, value: V) {
  var list = get(key)
  if (list != null && list.remove(value) && list.isEmpty()) {
    remove(key)
  }
}

fun <K, V> MutableMap<K, MutableList<V>>.putValue(key: K, value: V) {
  var list = get(key)
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