// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines

import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class VisualStickyLine(
  private val origin: StickyLine,
  private val primaryVisualLine: Int,
  private val scopeVisualLine: Int,
  var yLocation: Int = 0, // y coordinate to place sticky line component on the panel
) : StickyLine {

  override fun primaryLine(): Int = primaryVisualLine
  override fun scopeLine(): Int = scopeVisualLine
  override fun navigateOffset(): Int = origin.navigateOffset()
  override fun textRange(): TextRange = origin.textRange()
  override fun debugText(): String? = origin.debugText()

  override fun compareTo(other: StickyLine): Int {
    val compare = primaryLine().compareTo(other.primaryLine())
    if (compare != 0) {
      return compare
    }
    return other.scopeLine().compareTo(scopeLine())
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is VisualStickyLine) return false
    if (primaryVisualLine != other.primaryVisualLine) return false
    if (scopeVisualLine != other.scopeVisualLine) return false
    return true
  }

  override fun hashCode(): Int {
    var result = primaryVisualLine
    result = 31 * result + scopeVisualLine
    return result
  }

  override fun toString(): String {
    val debugText: String = debugText() ?: ""
    return "$debugText($primaryVisualLine, $scopeVisualLine, $yLocation)"
  }
}
