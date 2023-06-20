// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff

import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.openapi.ListSelection
import com.intellij.openapi.vcs.changes.Change
import git4idea.changes.GitBranchComparisonResult

sealed class ChangesSelection(
  val changes: List<Change>,
  val selectedIdx: Int
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ChangesSelection

    if (changes != other.changes) return false
    if (selectedIdx != other.selectedIdx) return false

    return true
  }

  override fun hashCode(): Int {
    var result = changes.hashCode()
    result = 31 * result + selectedIdx
    return result
  }

  class Single(changes: List<Change>, selectedIdx: Int, val location: DiffLineLocation? = null) : ChangesSelection(changes, selectedIdx) {

    constructor(changes: List<Change>, change: Change, location: DiffLineLocation?)
      : this(changes, changes.indexOfFirst { it === change }, location)

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false
      if (!super.equals(other)) return false

      other as Single

      return location == other.location
    }

    override fun hashCode(): Int {
      var result = super.hashCode()
      result = 31 * result + (location?.hashCode() ?: 0)
      return result
    }
  }

  class Multiple(changes: List<Change>, selectedIdx: Int = 0) : ChangesSelection(changes, selectedIdx)

  companion object {
    fun ListSelection<Change>.toSelection(location: DiffLineLocation? = null): ChangesSelection {
      return if (isExplicitSelection) {
        Multiple(list)
      }
      else {
        Single(list, selectedIndex, location)
      }
    }
  }
}

val ChangesSelection.selectedChange: Change?
  get() = selectedIdx.let { changes.getOrNull(it) }

internal fun Collection<Change>.isEqual(other: Collection<Change>): Boolean =
  equalsVia(other, Change::isEqual)

internal fun <E> Collection<E>.equalsVia(other: Collection<E>, isEqual: (E, E) -> Boolean): Boolean {
  if (other === this) return true
  if (size != other.size) return false

  val i1 = iterator()
  val i2 = other.iterator()

  while (i1.hasNext() && i2.hasNext()) {
    val e1 = i1.next()
    val e2 = i2.next()
    if (!isEqual(e1, e2)) return false
  }
  return !(i1.hasNext() || i2.hasNext())
}

internal fun Change.isEqual(other: Change): Boolean =
  GitBranchComparisonResult.REVISION_COMPARISON_HASHING_STRATEGY.equals(this, other)

//java.util.List.hashCode
internal fun List<Change>.calcHashCode(): Int {
  var hashCode = 1
  for (change in this) hashCode = 31 * hashCode + (change.calcHashCode())
  return hashCode
}

internal fun Change.calcHashCode(): Int =
  GitBranchComparisonResult.REVISION_COMPARISON_HASHING_STRATEGY.hashCode(this)