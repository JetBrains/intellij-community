// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import java.awt.font.FontRenderContext

/**
 * A cache key of [FontFallback].
 * The caller passes [variants] that never change, because a change breaks the lookup in the map.
 */
internal class FontKey(
  private val size: Float,
  private val useLigatures: Boolean,
  private val variants: Set<String>,
  private val context: FontRenderContext?,
) {
  fun matches(
    size: Float,
    useLigatures: Boolean,
    variants: Set<String>,
    context: FontRenderContext?,
  ): Boolean {
    return this.size.toBits() == size.toBits() &&
           this.useLigatures == useLigatures &&
           this.variants == variants &&
           this.context == context
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other !is FontKey) {
      return false
    }
    return matches(other.size, other.useLigatures, other.variants, other.context)
  }

  override fun hashCode(): Int {
    var result = size.hashCode()
    result = 31 * result + useLigatures.hashCode()
    result = 31 * result + variants.hashCode()
    result = 31 * result + context.hashCode()
    return result
  }

  override fun toString(): String {
    return "FontKey(size=$size, useLigatures=$useLigatures, variants=$variants, context=$context)"
  }
}
