// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view.animation

import java.awt.Rectangle
import java.awt.geom.Rectangle2D
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

internal data class EditorAnimationCacheKey(private val contentHash: Int)

internal data class CacheRequest(private val key: EditorAnimationCacheKey, private val rectangles: Supplier<List<Rectangle2D>>) {
  fun repaintedArea(
    visibleArea: Rectangle,
    pixelGrid: EditorPixelGrid,
  ) = rectangles.get()
    .reduceOrNull { union, rectangle -> union.createUnion(rectangle) }
    ?.intersectWithVisibleArea(visibleArea)
    ?.growToPixelGrid(pixelGrid)

  fun isDuplicate(previousKey: EditorAnimationCacheKey?): Boolean = key == previousKey

  fun pushKey(atomicKey: AtomicReference<EditorAnimationCacheKey?>) = atomicKey.set(key)
}
