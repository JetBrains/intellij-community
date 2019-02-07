// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph.utils

import gnu.trove.TIntHashSet

open class TIntHashSetFlags(private val size: Int) : Flags {
  val data = TIntHashSet()

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