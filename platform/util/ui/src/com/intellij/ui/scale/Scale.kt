// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ui.scale

import it.unimi.dsi.fastutil.doubles.Double2ObjectMap
import it.unimi.dsi.fastutil.doubles.Double2ObjectOpenHashMap
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*
import java.util.function.DoubleFunction

/**
 * A scale factor value of [ScaleType].
 *
 * @author tav
 */
@Internal
class Scale private constructor(val value: Double, val type: ScaleType) {
  companion object {
    // the cache radically reduces potential thousands of equal Scale instances
    private val cache = ThreadLocal.withInitial {
      EnumMap<ScaleType, Double2ObjectMap<Scale>>(ScaleType::class.java)
    }

    fun create(value: Double, type: ScaleType): Scale {
      return cache.get()
        .computeIfAbsent(type) { Double2ObjectOpenHashMap() }
        .computeIfAbsent(value, DoubleFunction { Scale(value, type) })
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Scale) return false

    if (value != other.value) return false
    return type == other.type
  }

  override fun hashCode(): Int {
    var result = value.hashCode()
    result = 31 * result + type.hashCode()
    return result
  }

  override fun toString(): String = "[${type.name} $value]"
}
