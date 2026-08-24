// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view.animation

import java.awt.geom.Rectangle2D

internal class CacheEntryList {
  private val entries = ArrayList<CacheEntry>()

  var totalArea: Double = 0.0
    private set

  fun clear() {
    entries.clear()
    totalArea = 0.0
  }

  fun findContaining(rectangle: Rectangle2D): CacheEntry? = entries.firstOrNull { it.contains(rectangle) }

  fun add(entry: CacheEntry) {
    entries.add(entry)
    totalArea += entry.area
  }

  fun removeIntersecting(rectangle: Rectangle2D) {
    if (entries.removeAll { it.intersects(rectangle) }) {
      totalArea = entries.sumOf { it.area }
    }
  }

  override fun toString(): String = "CacheEntryList(entries=$entries)"
}
