// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.FoldingModel
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class EmptyFoldingModel : FoldingModel, ModificationTracker {
  override fun getFoldRegion(startOffset: Int, endOffset: Int): FoldRegion? = null
  override fun getAllFoldRegions(): Array<FoldRegion> = emptyArray()
  override fun isOffsetCollapsed(offset: Int): Boolean = false
  override fun getCollapsedRegionAtOffset(offset: Int): FoldRegion? = null

  override fun addFoldRegion(startOffset: Int, endOffset: Int, placeholderText: String): FoldRegion? = null
  override fun removeFoldRegion(region: FoldRegion) = Unit

  @Deprecated("Deprecated in Java")
  override fun runBatchFoldingOperation(operation: Runnable, moveCaretFromCollapsedRegion: Boolean) {
    operation.run()
  }

  override fun runBatchFoldingOperation(operation: Runnable, allowMovingCaret: Boolean, keepRelativeCaretPosition: Boolean) {
    operation.run()
  }

  override fun getModificationCount(): Long = 0
}
