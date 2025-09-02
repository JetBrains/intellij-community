// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import kotlin.jvm.JvmStatic

enum class ThreeState {
  YES, NO, UNSURE;

  /**
   * Combine two different ThreeState values yielding UNSURE if values are different
   * and itself if values are the same.
   *
   * @param other other value to combine with this value
   * @return a result of combination of two ThreeState values
   */
  fun merge(other: ThreeState?): ThreeState {
    return if (this == other) this else UNSURE
  }

  fun toBoolean(): Boolean {
    check(this != UNSURE) { "Must be or YES, or NO" }
    return this == YES
  }

  /**
   * @param other state to compare with
   * @return true if the state is at least the same positive as the supplied one
   */
  fun isAtLeast(other: ThreeState): Boolean {
    return when (other) {
      YES -> this == YES
      UNSURE -> this != NO
      NO -> true
    }
  }

  companion object {
    @JvmStatic
    fun fromBoolean(value: Boolean): ThreeState {
      return if (value) YES else NO
    }

    /**
     * @return `YES` if the given states contain `YES`, otherwise `UNSURE` if the given states contain `UNSURE`, otherwise `NO`
     */
    @JvmStatic
    fun mostPositive(states: Iterable<ThreeState>): ThreeState {
      var result = NO
      for (state in states) {
        when (state) {
          YES -> return YES
          UNSURE -> result = UNSURE
          else -> continue
        }
      }
      return result
    }

    /**
     * @return `UNSURE` if `states` contains different values, the single value otherwise
     * @throws IllegalArgumentException if `states` is empty
     */
    @JvmStatic
    fun merge(states: Iterable<ThreeState>): ThreeState {
      var result: ThreeState? = null
      for (state in states) {
        if (state == UNSURE) {
          return UNSURE
        }
        if (result == null) {
          result = state
        }
        else if (result != state) {
          return UNSURE
        }
      }
      requireNotNull(result) { "Argument should not be empty" }
      return result
    }
  }
}