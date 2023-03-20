package com.intellij.util.io

import java.util.function.Predicate
import kotlin.math.min

private val illegalChars = hashSetOf('/', '\\', '?', '<', '>', ':', '*', '|', '"')

// https://github.com/parshap/node-sanitize-filename/blob/master/index.js
fun sanitizeFileName(name: String, replacement: String? = "_", truncateIfNeeded: Boolean = true, extraIllegalChars: Predicate<Char>? = null): String {
  var result: StringBuilder? = null
  var last = 0
  val length = name.length
  for (i in 0 until length) {
    val c = name[i]
    if (!illegalChars.contains(c) && !c.isISOControl() && (extraIllegalChars == null || !extraIllegalChars.test(c))) {
      continue
    }

    if (result == null) {
      result = StringBuilder()
    }
    if (last < i) {
      result.append(name, last, i)
    }

    if (replacement != null) {
      result.append(replacement)
    }
    last = i + 1
  }

  fun truncateFileName(s: String) = if (truncateIfNeeded) s.substring(0, min(length, 255)) else s

  if (result == null) {
    return truncateFileName(name)
  }

  if (last < length) {
    result.append(name, last, length)
  }

  return truncateFileName(result.toString())
}
