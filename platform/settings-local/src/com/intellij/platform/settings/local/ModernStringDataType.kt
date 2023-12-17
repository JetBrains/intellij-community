// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.local

import com.intellij.util.ArrayUtilRt
import org.h2.mvstore.DataUtils.readVarInt
import org.h2.mvstore.WriteBuffer
import org.h2.mvstore.type.BasicDataType
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * ```
 * StringBenchmark.bytes          thrpt   15  141 161 411,714 ± 1934825,849  ops/s
 * StringBenchmark.chars          thrpt   15   45 705 902,467 ±  227164,091  ops/s
 *
 * StringBenchmark.bytesNonAscii  thrpt   15   26 466 183,207 ±  663204,411  ops/s
 * StringBenchmark.charsNonAscii  thrpt   15   18 977 543,459 ±   37092,622  ops/s
 * ```
 *
 * The trick is that in a case of writing string via just `getBytes`, in most cases only `System.arrayCopy` will be involved in a modern JDK.
 */
@Internal
object ModernStringDataType : BasicDataType<String>() {
  override fun createStorage(size: Int): Array<String?> = if (size == 0) ArrayUtilRt.EMPTY_STRING_ARRAY else arrayOfNulls(size)

  override fun compare(a: String, b: String): Int = a.compareTo(b)

  override fun binarySearch(key: String, storageObj: Any, size: Int, initialGuess: Int): Int {
    @Suppress("UNCHECKED_CAST")
    val storage = storageObj as Array<String?>
    var low = 0
    var high = size - 1
    // the cached index minus one, so that for the first time (when cachedCompare is 0), the default value is used
    var x = initialGuess - 1
    if (x < 0 || x > high) {
      x = high ushr 1
    }
    while (low <= high) {
      val compare = key.compareTo(storage[x]!!)
      if (compare > 0) {
        low = x + 1
      }
      else if (compare < 0) {
        high = x - 1
      }
      else {
        return x
      }
      x = (low + high) ushr 1
    }
    return -(low + 1)
  }

  override fun getMemory(obj: String): Int = 24 + 2 * obj.length

  override fun read(buff: ByteBuffer): String {
    val bytes = ByteArray(readVarInt(buff))
    buff.get(bytes)
    return String(bytes, StandardCharsets.UTF_8)
  }

  override fun write(buff: WriteBuffer, s: String) {
    val bytes = s.encodeToByteArray()
    buff.putVarInt(bytes.size).put(bytes)
  }
}