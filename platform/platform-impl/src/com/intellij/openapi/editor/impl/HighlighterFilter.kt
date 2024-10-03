// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

/**
 * Internal state of [EditorImpl]'s filters.
 *
 * Not thread safe.
 * It's expected to be run on EDT.
 */
internal class HighlighterFilter private constructor(
  private var filterState: PersistentMap<Any, EditorHighlightingPredicate>,
  private var filters: Array<EditorHighlightingPredicate>
) {

  constructor() : this(persistentMapOf(), emptyArray())

  /**
   * @return null if the state has not changed, or the old state otherwise
   */
  @RequiresEdt
  fun addPredicate(key: Any, predicate: EditorHighlightingPredicate?): HighlighterFilter? {
    if (filterState[key] === predicate) {
      return null
    }

    val oldState = filterState
    val oldFilters = filters
    if (predicate == null) {
      filterState = filterState.remove(key)
    }
    else {
      filterState = filterState.put(key, predicate)
    }
    filters = filterState.values.toTypedArray()

    return HighlighterFilter(oldState, oldFilters)
  }

  fun shouldRender(highlighter: RangeHighlighter): Boolean =
    filters.all { it.shouldRender(highlighter) }
}
