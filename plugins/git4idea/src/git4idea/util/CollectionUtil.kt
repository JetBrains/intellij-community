// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.util

import git4idea.i18n.GitBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

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

fun <T> Collection<T>.toShortenedLogString(separator: @NonNls String = ", ", limit: Int = 20,
                                           transform: ((T) -> @NonNls CharSequence)? = null): @NonNls String {
  return joinToString(prefix = "[", postfix = "]",
                      separator = separator, limit = limit, truncated = truncated(limit),
                      transform = transform)
}

fun <K, V> Map<K, V>.toShortenedLogString(separator: @NonNls String = ", ",
                                          limit: Int = 20,
                                          transform: ((Map.Entry<K, V>) -> @NonNls CharSequence)? = null): @NonNls String {
  return asIterable().joinToString(prefix = "[", postfix = "]",
                                   separator = separator, limit = limit, truncated = truncated(size, limit),
                                   transform = transform)
}

private fun truncated(size: Int, limit: Int) : @NonNls String = " ... +${size - limit} more"
private fun Collection<*>.truncated(limit: Int) = truncated(size, limit)