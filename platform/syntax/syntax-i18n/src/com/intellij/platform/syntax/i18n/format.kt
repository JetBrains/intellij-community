// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.i18n

internal fun format(raw: String, params: Array<out Any>): String {
  val builder = StringBuilder(raw.length + 16 * params.size)

  var inQuote = false
  var indexBuilder: StringBuilder? = null

  var i = -1
  while (true) {
    i++
    if (i >= raw.length) break
    when (val c = raw[i]) {
      '\'' -> {
        if (indexBuilder != null) {
          throw IllegalArgumentException("Index expression must not contain single quotes: $raw, position $i")
        }
        if (raw.charAtIs(i + 1, '\'')) {
          builder.append(c)
          i++
          continue
        }
        inQuote = !inQuote
      }
      '{' -> {
        if (inQuote) {
          builder.append(c)
          continue
        }
        indexBuilder = StringBuilder()
      }
      '}' -> {
        if (inQuote) {
          builder.append(c)
          continue
        }
        if (indexBuilder == null) {
          builder.append(c)
          continue
        }
        val injectionIndex = indexBuilder.toString().toIntOrNull()
        if (injectionIndex != null && injectionIndex >= 0 && injectionIndex < params.size) {
          builder.append(params[injectionIndex])
        }
        else {
          builder.append('{').append(indexBuilder).append('}')
        }
        indexBuilder = null
      }
      else -> {
        if (indexBuilder != null) {
          indexBuilder.append(c)
        }
        else {
          builder.append(c)
        }
      }
    }
  }

  return builder.toString()
}

private fun String.charAtIs(i: Int, c: Char): Boolean = i < length && this[i] == c