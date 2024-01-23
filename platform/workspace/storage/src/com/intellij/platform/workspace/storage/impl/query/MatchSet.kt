// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.query

/**
 * Collection of matches grouped by operation type
 */
internal class MatchSet() {

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
}
