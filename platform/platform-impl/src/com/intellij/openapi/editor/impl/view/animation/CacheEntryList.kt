// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view.animation

import java.awt.geom.Rectangle2D

internal class CacheEntryList {
  private val entries = ArrayList<CacheEntry>()

  private var totalArea: Double = 0.0

  fun clear() {
    entries.clear()
    totalArea = 0.0
  }

  fun findContaining(rectangle: Rectangle2D): CacheEntry? = entries.firstOrNull { it.contains(rectangle) }

  /**
   * Whether holding [rectangle] as well would take the cache over [budget].
   */
  fun wouldExceed(budget: Double, rectangle: Rectangle2D): Boolean {
    return totalArea + rectangle.area > budget
  }

  fun add(entry: CacheEntry) {
    entries.add(entry)
    totalArea += entry.area
  }

  fun removeIntersecting(rectangle: Rectangle2D): Boolean {
    val removed = entries.removeAll { it.intersects(rectangle) }
    if (removed) {
      totalArea = entries.sumOf { it.area }
    }
    return removed
  }

  override fun toString(): String = "CacheEntryList(entries=$entries)"
}
