// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.openapi.util.Comparing
import org.jetbrains.annotations.ApiStatus
import java.awt.Color

/**
 * This is a workaround to the [java.awt.Color.equals] not checking `o.class`, thus introducing non-changeable equality
 * that works only on the current-RGB-value basis.
 * Ex: it would consider two different [com.intellij.ui.JBColor.namedColor] for "Tree.background" and "Table.background" as equal and interchangeable
 * if their RGB values match in the current theme.
 *
 * This may introduce issues when the Color is used as a key in some cache.
 *
 * Note: the Supplier func in [com.intellij.ui.JBColor] may implement the interface as well
 * @see com.intellij.util.ui.UIUtil.equalColors
 */
@ApiStatus.Internal
interface ComparableColor {
  fun colorEquals(other: ComparableColor): Boolean
  fun colorHashCode(): Int

  companion object {
    @JvmStatic
    fun equalColors(c1: Color?, c2: Color?): Boolean {
      return equalComparable(c1, c2)
    }

    @JvmStatic
    fun colorHashCode(color: Color?): Int {
      return comparableHashCode(color)
    }

    @JvmStatic
    fun equalComparable(c1: Any?, c2: Any?): Boolean {
      if (c1 is ComparableColor && c2 is ComparableColor) {
        return c1.colorEquals(c2)
      }
      if (c1 == null || c2 == null) return c1 === c2
      return c1.javaClass == c2.javaClass &&
             Comparing.equal(c1, c2)
    }

    @JvmStatic
    fun comparableHashCode(color: Any?): Int {
      if (color == null) return 0
      if (color is ComparableColor) {
        return color.colorHashCode()
      }
      return color.hashCode()
    }
  }
}
