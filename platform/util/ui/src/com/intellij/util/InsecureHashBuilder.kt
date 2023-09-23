// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.util

import com.dynatrace.hash4j.hashing.HashFunnel
import com.intellij.ui.hasher
import it.unimi.dsi.fastutil.longs.LongArrayList
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
class InsecureHashBuilder {
  private val result = LongArrayList()
  private val hashStream = hasher.hashStream()

  fun build(): LongArray {
    return result.toLongArray()
  }

  fun update(value: CharSequence): InsecureHashBuilder {
    hashStream.putChars(value)
    return addAndReset()
  }

  private fun addAndReset(): InsecureHashBuilder {
    result.add(hashStream.asLong)
    hashStream.reset()
    return this
  }

  fun update(value: LongArray): InsecureHashBuilder {
    hashStream.putLongArray(value)
    return addAndReset()
  }

  fun update(value: Int): InsecureHashBuilder {
    hashStream.putInt(value)
    return addAndReset()
  }

  fun update(value: Long): InsecureHashBuilder {
    hashStream.putLong(value)
    return addAndReset()
  }

  fun stringList(list: List<String>): InsecureHashBuilder {
    hashStream.putOrderedIterable(list, HashFunnel.forString())
    return addAndReset()
  }

  fun stringMap(map: Map<String, String>): InsecureHashBuilder {
    hashStream.putUnorderedIterable(map.entries, HashFunnel.forEntry(
      HashFunnel.forString(),
      HashFunnel.forString()), hasher)
    return addAndReset()
  }

  fun stringIntMap(map: Map<String, Int>): InsecureHashBuilder {
    hashStream.putUnorderedIterable(map.entries, HashFunnel.forEntry(
      HashFunnel.forString(),
      HashFunnel { v, sink -> sink.putInt(v) }
    ), hasher)
    addAndReset()
    return this
  }
}
