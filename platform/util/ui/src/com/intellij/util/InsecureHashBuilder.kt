// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.util

import it.unimi.dsi.fastutil.longs.LongArrayList
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.xxh3.Access
import org.jetbrains.xxh3.Xxh3
import org.jetbrains.xxh3.Xxh3Impl
import java.util.*

private const val extraSeed = 1867412186L

// see https://github.com/Cyan4973/xxHash/wiki/Collision-ratio-comparison
@Experimental
class InsecureHashBuilder {
  private val result = LongArrayList()

  fun build(): LongArray {
    return result.toLongArray()
  }

  fun update(value: CharSequence) {
    result.add(Xxh3.hashUnencodedChars(value))
  }

  fun update(value: LongArray): InsecureHashBuilder {
    result.add(Xxh3.hashLongs(value))
    return this
  }

  fun update(value: Int) {
    result.add(Xxh3.hashInt(value))
  }

  fun updateRaw(hash: Long) {
    result.add(hash)
  }

  fun stringList(list: List<String>) {
    val size = list.size
    result.add(Xxh3.hashInt(size))

    if (list.isEmpty()) {
      return
    }

    val offset = result.size
    result.ensureCapacity(size)
    for (s in list) {
      result.add(Xxh3.hashUnencodedChars(s))
    }
    hash128(offset)
  }

  fun stringMap(map: Map<String, String>): InsecureHashBuilder {
    val size = map.size
    result.add(Xxh3.hashInt(size))

    if (map.isEmpty()) {
      return this
    }

    val offset = result.size
    result.ensureCapacity(size * 2)

    if (map is SortedMap || map is LinkedHashMap) {
      for ((k, v) in map) {
        result.add(Xxh3.hashUnencodedChars(k))
        result.add(Xxh3.hashUnencodedChars(v))
      }
    }
    else {
      for (k in map.keys.sorted()) {
        result.add(Xxh3.hashUnencodedChars(k))
        val v = map.get(k)
        result.add(if (v == null) 0 else Xxh3.hashUnencodedChars(v))
      }
    }
    hash128(offset)
    return this
  }

  fun stringIntMap(map: Map<String, Int?>): InsecureHashBuilder {
    val size = map.size
    result.add(Xxh3.hashInt(size))

    if (map.isEmpty()) {
      return this
    }

    val offset = result.size
    result.ensureCapacity(size * 2)

    if (map is SortedMap || map is LinkedHashMap) {
      for ((k, v) in map) {
        result.add(Xxh3.hashUnencodedChars(k))
        result.add(if (v == null) 0 else Xxh3.hashInt(v, 0))
      }
    }
    else {
      for (k in map.keys.sorted()) {
        result.add(Xxh3.hashUnencodedChars(k))
        val v = map.get(k)
        result.add(if (v == null) 0 else Xxh3.hashInt(v, 0))
      }
    }

    hash128(offset)
    return this
  }

  private fun hash128(offset: Int) {
    val sizeInBytes = (result.size - offset) * Long.SIZE_BYTES
    val offsetInBytes = offset * Long.SIZE_BYTES
    val l1 = Xxh3Impl.hash(result, LongListAccessForLongs, offsetInBytes, sizeInBytes, 0)
    // and with a custom seed to get a 128-bit hash
    val l2 = Xxh3Impl.hash(result, LongListAccessForLongs, offsetInBytes, sizeInBytes, extraSeed)
    result.size(offset)
    result.add(l1)
    result.add(l2)
  }
}

// special implementation for hashing long array - it is guaranteed that only i64 will be called (as input is aligned)
object LongListAccessForLongs : Access<LongArrayList> {
  override fun i64(input: LongArrayList, offset: Int): Long {
    return input.getLong(offset shr 3)
  }

  override fun i32(input: LongArrayList, offset: Int): Int {
    val v = input.getLong(offset shr 3)
    return if (offset and 7 == 0) (v shr 32).toInt() else v.toInt()
  }

  override fun i8(input: LongArrayList, offset: Int): Int {
    throw UnsupportedOperationException()
  }
}

