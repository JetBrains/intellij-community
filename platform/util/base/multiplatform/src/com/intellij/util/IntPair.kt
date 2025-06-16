// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import kotlin.jvm.JvmField

class IntPair(@JvmField val first: Int, @JvmField val second: Int) {
  override fun hashCode(): Int {
    return 31 * first + second
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || this::class != other::class) return false

    val that = other as IntPair
    return first == that.first && second == that.second
  }

  override fun toString(): String {
    return "first=$first, second=$second"
  }
}
