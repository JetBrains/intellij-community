// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.impl.builder

import com.intellij.platform.syntax.impl.fastutil.ints.IntArrayList
import com.intellij.platform.syntax.impl.fastutil.ints.isEmpty

internal open class MarkerPool(
  private val builder: ParsingTreeBuilder,
) {
  private val freeStartMarkers = IntArrayList()
  private val freeErrorItems = IntArrayList()
  private val list = ArrayList<ProductionMarker>()

  init {
    list.add(CompositeMarker(-1, builder)) //no marker has id 0
  }

  fun allocateCompositeMarker(): CompositeMarker {
    if (!freeStartMarkers.isEmpty()) {
      return list[freeStartMarkers.pop()] as CompositeMarker
    }

    val marker = CompositeMarker(list.size, builder)
    list.add(marker)
    return marker
  }

  fun allocateErrorNode(): ErrorMarker {
    if (!freeErrorItems.isEmpty()) {
      return list[freeErrorItems.pop()] as ErrorMarker
    }

    val item = ErrorMarker(list.size, builder)
    list.add(item)
    return item
  }

  fun freeMarker(marker: ProductionMarker) {
    marker.dispose()
    val freeMarkerStack = if (marker is CompositeMarker) freeStartMarkers else freeErrorItems
    freeMarkerStack.push(marker.markerId)
  }

  fun get(index: Int): ProductionMarker =
    list[index]
}

private fun IntArrayList.pop(): Int = removeAt(size - 1)
private fun IntArrayList.push(i: Int) = add(i)