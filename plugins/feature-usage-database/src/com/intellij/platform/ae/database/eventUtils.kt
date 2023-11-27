// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ae.database

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.ae.database.activities.UserActivity
import kotlinx.coroutines.*
import org.jetbrains.sqlite.SqliteResultSet

object AEEventUtils

/**
 * Wrap the call to your update() function in user activity into this method,
 * so activity won't be updated when not needed (right now – in unit test mode)
 *
 * @param activity user activity that will be updated
 * @param action code that updates activity, is a suspend lambda. [activity] is passed into lambda
 */
inline fun <T : UserActivity> CoroutineScope.runUpdateEvent(activity: T, crossinline action: suspend (T) -> Unit) {
  if (ApplicationManager.getApplication().isUnitTestMode) return
  launch {
    withContext(Dispatchers.Default) {
      try {
        action(activity)
      }
      catch (t: CancellationException) {
        throw t
      }
      catch (t: Throwable) {
        logger<AEEventUtils>().error(t)
      }
    }
  }
}

internal fun formatString(params: Map<String, String>): String {
  return buildString {
    append("{")

    val totalSize = params.size
    for (param in params.asIterable().withIndex()) {
      escape(param.value.key, this)
      append(": ")
      escape(param.value.value, this)
      if (totalSize-1 != param.index) {
        append(", ")
      }
    }
    append("}")
  }
}

fun <K0, V0, K, V> createMap(getKey: (SqliteResultSet) -> K0, getValue: (SqliteResultSet) -> V0,
                                      validate: (K0, V0) -> Boolean,
                                      transformKey: (K0) -> K, transformValue: (V0) -> V,
                                      validateTransformed: (K, V) -> Boolean,
                                      res: SqliteResultSet): Map<K, V> {
  val map = mutableMapOf<K, V>()
  while (res.next()) {
    val k0 = getKey(res)
    val v0 = getValue(res)
    if (!validate(k0, v0)) continue

    val k = transformKey(k0)
    val v = transformValue(v0)
    if (!validateTransformed(k, v)) continue

    map[k] = v
  }

  return map
}

private val REPLACEMENT_CHARS = arrayOfNulls<String>(128).apply {
  for (i in 0..31) {
    this[i] = String.format("\\u%04x", i)
  }
  this['"'.code] = "\\\""
  this['\\'.code] = "\\\\"
  this['\t'.code] = "\\t"
  this['\b'.code] = "\\b"
  this['\n'.code] = "\\n"
  this['\r'.code] = "\\r"
  this['\u000c'.code] = "\\f"
}

// copy-paste of [org.jetbrains.io.JsonUtil], not to depend on platform-impl
private fun escape(value: CharSequence, sb: StringBuilder) {
  val length = value.length
  sb.ensureCapacity(sb.length + length + 2)
  sb.append('"')
  var last = 0
  for (i in 0 until length) {
    val c = value[i]
    var replacement: String?
    if (c.code < 128) {
      replacement = REPLACEMENT_CHARS.get(c.code)
      if (replacement == null) {
        continue
      }
    }
    else if (c == '\u2028') {
      replacement = "\\u2028"
    }
    else if (c == '\u2029') {
      replacement = "\\u2029"
    }
    else {
      continue
    }
    if (last < i) {
      sb.append(value, last, i)
    }
    sb.append(replacement)
    last = i + 1
  }
  if (last < length) {
    sb.append(value, last, length)
  }
  sb.append('"')
}