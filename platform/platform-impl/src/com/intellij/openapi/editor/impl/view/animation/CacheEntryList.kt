// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view.animation

import java.awt.geom.Area
import java.awt.geom.Rectangle2D

internal class CacheEntryList {
  private val entries = ArrayList<CacheEntry>()
  private var totalArea: Double = 0.0

  fun add(entry: CacheEntry, budget: Double): Boolean {
    val exceedsBudget = totalArea + entry.area() > budget
    if (exceedsBudget) {
      clear()
    }
    entries.add(entry)
    totalArea += entry.area()
    return exceedsBudget
  }

  fun clear() {
    entries.clear()
    totalArea = 0.0
  }

  fun findContaining(rectangle: Rectangle2D): CacheEntry? {
    return entries.firstOrNull { it.contains(rectangle) }
  }

  fun borderShape(rectangle: Rectangle2D): Area {
    val shape = Area()
    for (entry in entries) {
      if (entry.intersects(rectangle)) {
        entry.addBorderTo(shape)
      }
    }
    return shape
  }

  fun removeIntersecting(rectangle: Rectangle2D): Boolean {
    val removed = entries.removeAll { it.intersects(rectangle) }
    if (removed) {
      totalArea = entries.sumOf { it.area() }
    }
    return removed
  }

  override fun toString(): String = "CacheEntryList(entries=$entries)"
}
