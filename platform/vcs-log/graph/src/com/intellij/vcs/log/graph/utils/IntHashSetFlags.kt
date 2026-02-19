// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.utils

import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class IntHashSetFlags(private val size: Int) : Flags {
  val data = IntOpenHashSet()

  override fun size(): Int = size

  override fun get(index: Int): Boolean = data.contains(index)

  override fun set(index: Int, value: Boolean) {
    if (value) {
      data.add(index)
    }
    else {
      data.remove(index)
    }
  }

  override fun setAll(value: Boolean) {
    if (value) {
      for (i in 0 until size) {
        data.add(i)
      }
    }
    else {
      data.clear()
    }
  }
}