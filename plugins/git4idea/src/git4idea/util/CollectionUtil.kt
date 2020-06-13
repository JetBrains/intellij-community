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

fun <T> Collection<T>.toShortenedString(separator: String = ", ", limit: Int = 20,
                                        transform: ((T) -> CharSequence)? = null): String {
  return joinToString(prefix = "[", postfix = "]",
                      separator = separator, limit = limit, truncated = truncated(limit),
                      transform = transform)
}

fun <K, V> Map<K, V>.toShortenedString(separator: String = ", ",
                                       limit: Int = 20,
                                       transform: ((Map.Entry<K, V>) -> CharSequence)? = null): String {
  return asIterable().joinToString(prefix = "[", postfix = "]",
                                   separator = separator, limit = limit, truncated = truncated(size, limit),
                                   transform = transform)
}

private fun truncated(size: Int, limit: Int) = " ... +${size - limit} more"
private fun Collection<*>.truncated(limit: Int) = truncated(size, limit)