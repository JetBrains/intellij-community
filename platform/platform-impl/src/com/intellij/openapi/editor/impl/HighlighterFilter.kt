// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.rd.util.remove

/**
 * Internal state of [EditorImpl]'s filters.
 *
 * Not thread safe.
 * It's expected to be run on EDT.
 */
internal class HighlighterFilter private constructor(
  private var predicates: Array<EditorHighlightingPredicate>
) {

  constructor() : this(emptyArray())

  /**
   * @return null if the state has not changed, or the old state otherwise
   */
  @RequiresEdt
  fun updatePredicate(predicate: EditorHighlightingPredicate, oldPredicate: EditorHighlightingPredicate?): HighlighterFilter? {
    if (oldPredicate == null) {
      if (predicate in predicates) {
        return null
      }

      val oldPredicates = predicates
      predicates += predicate

      return HighlighterFilter(oldPredicates)
    }

    val index = predicates.indexOf(oldPredicate)
    if (index == -1) {
      logger<HighlighterFilter>().error("Predicate $oldPredicate is missing in the list")
      return updatePredicate(predicate, null)
    }

    val oldPredicates = predicates
    val newPredicates = predicates.copyOf()
    newPredicates[index] = predicate
    predicates = newPredicates

    return HighlighterFilter(oldPredicates)
  }

  /**
   * @return null if the state has not changed, or the old state otherwise
   */
  @RequiresEdt
  fun removePredicate(predicate: EditorHighlightingPredicate): HighlighterFilter? {
    val oldPredicates = predicates
    predicates = predicates.remove(predicate)

    if (oldPredicates === predicates) {
      // predicate was missing in the array, thus the array has not been changed by `remove` operation
      logger<HighlighterFilter>().error("Predicate $predicate is missing in the list")
      return null
    }

    return HighlighterFilter(oldPredicates)
  }

  fun shouldRender(highlighter: RangeHighlighter): Boolean =
    predicates.all { it.shouldRender(highlighter) }
}
