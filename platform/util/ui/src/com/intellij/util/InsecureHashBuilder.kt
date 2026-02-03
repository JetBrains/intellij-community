// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.util

import com.dynatrace.hash4j.hashing.HashStream64
import com.dynatrace.hash4j.hashing.Hasher64
import com.dynatrace.hash4j.hashing.Hashing
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

@Experimental
@Internal
class InsecureHashBuilder {
  companion object {
    @JvmField
    val seededHasher: Hasher64 = Hashing.komihash5_0(-1870338434679095961)

    private fun firstHasher(): Hasher64 = Hashing.komihash5_0()
  }

  private val hashStream = firstHasher().hashStream()
  private val hashStream2 = seededHasher.hashStream()

  fun build(): LongArray = longArrayOf(hashStream.asLong, hashStream2.asLong)

  fun putBoolean(b: Boolean): InsecureHashBuilder {
    hashStream.putBoolean(b)
    return this
  }

  fun putString(s: String): InsecureHashBuilder {
    hashStream.putString(s)
    return this
  }

  fun putInt(value: Int): InsecureHashBuilder {
    hashStream.putInt(value)
    return this
  }

  fun putLong(value: Long): InsecureHashBuilder {
    hashStream.putLong(value)
    return this
  }

  fun putStringMap(map: Map<String, String>): InsecureHashBuilder {
    val entryHashes = LongArray(map.size)
    hashUnorderedStringStringMap(entryHasher = firstHasher(), finalHashStream = hashStream, map = map, elementHashes = entryHashes)
    hashUnorderedStringStringMap(entryHasher = seededHasher, finalHashStream = hashStream2, map = map, elementHashes = entryHashes)
    return this
  }

  fun putStringIntMap(map: Map<String, Int>): InsecureHashBuilder {
    val entryHashes = LongArray(map.size)
    hashUnorderedStringIntMap(entryHasher = firstHasher(), finalHashStream = hashStream, map = map, elementHashes = entryHashes)
    hashUnorderedStringIntMap(entryHasher = seededHasher, finalHashStream = hashStream2, map = map, elementHashes = entryHashes)
    return this
  }
}

private fun hashUnorderedStringStringMap(entryHasher: Hasher64,
                                         finalHashStream: HashStream64,
                                         map: Map<String, String>,
                                         elementHashes: LongArray) {
  var index = 0
  val stream = entryHasher.hashStream()
  for ((k, v) in map) {
    elementHashes[index++] = stream.reset().putString(k).putString(v).asLong
  }
  elementHashes.sort()

  finalHashStream.putLongArray(elementHashes)
  finalHashStream.putInt(elementHashes.size)
}

private fun hashUnorderedStringIntMap(entryHasher: Hasher64,
                                      finalHashStream: HashStream64,
                                      map: Map<String, Int>,
                                      elementHashes: LongArray) {
  var index = 0
  val stream = entryHasher.hashStream()
  for ((k, v) in map) {
    elementHashes[index++] = stream.reset().putString(k).putInt(v).asLong
  }
  elementHashes.sort()

  finalHashStream.putLongArray(elementHashes)
  finalHashStream.putInt(elementHashes.size)
}
