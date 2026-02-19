// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.runtime

import org.jetbrains.annotations.ApiStatus
import kotlin.jvm.JvmInline

/**
 * Encapsulates an integer mask to provide functionality using bitwise operations for controlling parser behavior.
 *
 * @author Maxim Medvedev
 */
@ApiStatus.Experimental
@JvmInline
value class Modifiers private constructor(private val mask: Int) {

  infix fun and(other: Modifiers): Modifiers = Modifiers(mask and other.mask)

  infix fun or(other: Modifiers): Modifiers = Modifiers(mask or other.mask)

  companion object {
    val _NONE_: Modifiers = Modifiers(0x0)
    val _COLLAPSE_: Modifiers = Modifiers(0x1)
    val _LEFT_: Modifiers = Modifiers(0x2)
    val _LEFT_INNER_: Modifiers = Modifiers(0x4)
    val _AND_: Modifiers = Modifiers(0x8)
    val _NOT_: Modifiers = Modifiers(0x10)
    val _UPPER_: Modifiers = Modifiers(0x20)
  }
}