// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed

enum class CommittedChangesFilterPriority(val priority: Int) {
  TEXT(0),
  STRUCTURE(1),
  USER(2),
  MERGE(3),
  NONE(4)
}

data class CommittedChangesFilterKey(
  private val id: String,
  private val priority: CommittedChangesFilterPriority
) : Comparable<CommittedChangesFilterKey> {

  override fun compareTo(other: CommittedChangesFilterKey): Int = compareValuesBy(this, other) { priority.priority }
}