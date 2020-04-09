// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.util

fun <K, V> Map<K, V>.without(removed: K): Map<K, V> {
  val result = this.toMutableMap()
  result.remove(removed)
  return result
}

fun <R> List<*>.lastInstance(klass: Class<R>): R? {
  val iterator = this.listIterator(size)
  while (iterator.hasPrevious()) {
    val element = iterator.previous()
    @Suppress("UNCHECKED_CAST")
    if (klass.isInstance(element)) return element as R
  }
  return null
}

fun <T> Collection<T>.toShortenedString(num: Int = 20): String {
  if (size < num) return toString()
  return "${take(num)} ... +${size - num} more"
}

fun <K, V> Map<K, V>.toShortenedString(num: Int = 20): String {
  if (size < num) return toString()
  return "${asIterable().take(num).toMap()} ... +${size - num} more"
}

private fun <K, V> Iterable<Map.Entry<K, V>>.toMap(): Map<K, V> {
  val result = mutableMapOf<K, V>()
  for (entry in this) {
    result[entry.key] = entry.value
  }
  return result
}