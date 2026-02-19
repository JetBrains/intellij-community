// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.ui.scale.JBUIScale
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.math.roundToInt


/**
 * Internal class used for scaling integer values based on the current scale factor.
 *
 * Example:
 * // userScale == 1.0
 * val scaler = ValueScaler(2)
 * ...
 * // userScale == 1.5
 * val value = scaler.get() // value == 3
 *
 * @property origValue The original value scaled with the userScale at the moment of ValueScaler initialization
 */
@Internal
class ValueScaler(private val origValue: Int) {
  private val origScale = JBUIScale.scale(1f)

  fun get(): Int {
    val currentScale = JBUIScale.scale(1f)
    return if (origScale == currentScale) origValue
    else (origValue.toFloat() * currentScale / origScale).roundToInt()
  }
}