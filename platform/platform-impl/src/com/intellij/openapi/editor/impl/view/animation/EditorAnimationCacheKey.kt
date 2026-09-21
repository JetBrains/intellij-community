// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view.animation

import com.intellij.openapi.editor.impl.caret.model.CaretRectangle

/**
 * Identifies the content a cache request covers, so a request for content already held can be dropped.
 */
internal data class EditorAnimationCacheKey(private val contentHash: Int) {
  companion object {
    @JvmStatic
    fun of(locations: List<CaretRectangle>): EditorAnimationCacheKey {
      var hash = locations.size
      for (location in locations) {
        hash = hash * 31 + location.contentHash
      }
      return EditorAnimationCacheKey(hash)
    }
  }
}
