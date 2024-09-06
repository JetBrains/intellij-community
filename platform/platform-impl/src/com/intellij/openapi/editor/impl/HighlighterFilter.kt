// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.markup.RangeHighlighter
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

/**
 * Not thread safe. It's expected to be run on EDT
 */
internal class HighlighterFilter private constructor(
  private var filterState: PersistentMap<Any, EditorHighlightingPredicate>
) {

  constructor() : this(persistentMapOf())

  /**
   * @return null if the state has not changed, or the old state otherwise
   */
  fun addPredicate(key: Any, predicate: EditorHighlightingPredicate?): HighlighterFilter? {
    if (filterState[key] === predicate) {
      return null
    }

    val oldState = filterState
    if (predicate == null) {
      filterState = filterState.remove(key)
    }
    else {
      filterState = filterState.put(key, predicate)
    }

    return HighlighterFilter(oldState)
  }

  fun test(highlighter: RangeHighlighter): Boolean =
    filterState.values.all { it.test(highlighter) }
}
