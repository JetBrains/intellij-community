// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.util

import com.dynatrace.hash4j.hashing.HashStream64
import com.intellij.ui.hasher
import com.intellij.ui.seededHasher
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
class InsecureHashBuilder {
  private val hashStream = hasher.hashStream()
  private val hashStream2 = seededHasher.hashStream()

  fun build(): LongArray = longArrayOf(hashStream.asLong, hashStream2.asLong)

  //fun putChars(value: CharSequence): InsecureHashBuilder {
  //  hashStream.putChars(value)
  //  return this
  //}

  fun putString(s: String): InsecureHashBuilder {
    hashStream.putString(s)
    return this
  }

  fun putLongArray(value: LongArray): InsecureHashBuilder {
    hashStream.putLongArray(value)
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

  //fun putStringList(list: List<String>): InsecureHashBuilder {
  //  hashStream.putOrderedIterable(list, HashFunnel.forString())
  //  return this
  //}

  fun putStringMap(map: Map<String, String>): InsecureHashBuilder {
    val entryHashes = LongArray(map.size)
    hashUnorderedStringStringMap(entryHashStream = hasher.hashStream(),
                                 finalHashStream = hashStream,
                                 map = map,
                                 elementHashes = entryHashes)
    hashUnorderedStringStringMap(entryHashStream = seededHasher.hashStream(),
                                 finalHashStream = hashStream2,
                                 map = map,
                                 elementHashes = entryHashes)
    return this
  }

  fun putStringIntMap(map: Map<String, Int>): InsecureHashBuilder {
    val entryHashes = LongArray(map.size)
    hashUnorderedStringIntMap(entryHashStream = hasher.hashStream(),
                              finalHashStream = hashStream,
                              map = map,
                              elementHashes = entryHashes)
    hashUnorderedStringIntMap(entryHashStream = seededHasher.hashStream(),
                              finalHashStream = hashStream2,
                              map = map,
                              elementHashes = entryHashes)
    return this
  }
}

private fun hashUnorderedStringStringMap(entryHashStream: HashStream64,
                                         finalHashStream: HashStream64,
                                         map: Map<String, String>,
                                         elementHashes: LongArray) {
  var index = 0
  for ((k, v) in map) {
    elementHashes[index++] = entryHashStream.reset().putString(k).putString(v).asLong
  }
  elementHashes.sort()

  finalHashStream.putLongArray(elementHashes)
  finalHashStream.putInt(elementHashes.size)
}

private fun hashUnorderedStringIntMap(entryHashStream: HashStream64,
                                      finalHashStream: HashStream64,
                                      map: Map<String, Int>,
                                      elementHashes: LongArray) {
  var index = 0
  for ((k, v) in map) {
    elementHashes[index++] = entryHashStream.reset().putString(k).putInt(v).asLong
  }
  elementHashes.sort()

  finalHashStream.putLongArray(elementHashes)
  finalHashStream.putInt(elementHashes.size)
}
