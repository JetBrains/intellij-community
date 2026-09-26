// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.CustomFoldRegionRenderer
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.FoldingGroup
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface FoldRegionMarker : FoldRegion, RangeMarkerEx {
  fun setExpanded(expanded: Boolean, notify: Boolean)

  fun setExpandedInternal(expanded: Boolean)

  fun hasDocumentRegionChanged(): Boolean

  fun markDocumentRegionChanged()

  fun resetDocumentRegionChanged()
}

internal interface CustomFoldRegionMarker : FoldRegionMarker, CustomFoldRegion

internal interface FoldingRegionStorage {
  fun createFoldRegion(
    startOffset: Int,
    endOffset: Int,
    placeholder: String,
    group: FoldingGroup?,
    neverExpands: Boolean,
  ): FoldRegionMarker

  fun createCustomFoldRegion(
    startOffset: Int,
    endOffset: Int,
    renderer: CustomFoldRegionRenderer,
  ): CustomFoldRegionMarker

  fun removeRegion(region: FoldRegionMarker): Boolean

  fun clearRegions()

  fun dispose()

  fun size(): Int

  fun processAllRegions(processor: Processor<in FoldRegionMarker>): Boolean

  fun processRegionsContaining(offset: Int, processor: Processor<in FoldRegionMarker>): Boolean

  fun processRegionsOverlappingWith(startOffset: Int, endOffset: Int, processor: Processor<in FoldRegionMarker>): Boolean

  fun beforeDocumentChange(event: DocumentEvent) {
  }
}
