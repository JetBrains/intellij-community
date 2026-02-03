// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.query

/**
 * Collection of matches grouped by operation type
 */
internal class MatchList {

  private val addedMatches: MutableList<Match> = mutableListOf()
  private val removedMatches: MutableList<Match> = mutableListOf()

  fun addedMatch(match: Match) {
    addedMatches += match
  }

  fun removedMatch(match: Match) {
    removedMatches += match
  }

  fun addedMatches(): List<Match> {
    return addedMatches
  }

  fun removedMatches(): List<Match> {
    return removedMatches
  }

  fun isEmpty(): Boolean {
    return addedMatches.isEmpty() && removedMatches.isEmpty()
  }

  fun removeMatches(matchSet: MatchSet) {
    val addedIterator = addedMatches.iterator()
    while (addedIterator.hasNext()) {
      val next = addedIterator.next()
      if (matchSet.containsAdded(next)) addedIterator.remove()
    }

    val removedIterator = removedMatches.iterator()
    while (removedIterator.hasNext()) {
      val next = removedIterator.next()
      if (matchSet.containsRemoved(next)) removedIterator.remove()
    }
  }
}

internal class MatchSet {

  private val addedMatches: MutableSet<Match> = mutableSetOf()
  private val removedMatches: MutableSet<Match> = mutableSetOf()

  fun contains(match: Match): Boolean = addedMatches.contains(match) || removedMatches.contains(match)

  fun addFromList(matchList: MatchList) {
    matchList.addedMatches().forEach { this.addedMatches.add(it) }
    matchList.removedMatches().forEach { this.removedMatches.add(it) }
  }

  fun containsAdded(match: Match): Boolean = addedMatches.contains(match)
  fun containsRemoved(match: Match): Boolean = removedMatches.contains(match)
}
