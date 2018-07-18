// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.vcs.changes.Change

private val MODIFIED_FILTER = { it: Change -> it.type == Change.Type.MODIFICATION || it.type == Change.Type.MOVED }
private val NEW_FILTER = { it: Change -> it.type == Change.Type.NEW }
private val DELETED_FILTER = { it: Change -> it.type == Change.Type.DELETED }

class ChangeInfoCalculator : CommitLegendPanel.InfoCalculator {
  private var myDisplayedChanges = emptyList<Change>()
  private var myIncludedChanges = emptyList<Change>()

  override val new get() = myDisplayedChanges.count(NEW_FILTER)
  override val modified get() = myDisplayedChanges.count(MODIFIED_FILTER)
  override val deleted get() = myDisplayedChanges.count(DELETED_FILTER)
  override var unversioned: Int = 0
    private set

  override val includedNew get() = myIncludedChanges.count(NEW_FILTER)
  override val includedModified get() = myIncludedChanges.count(MODIFIED_FILTER)
  override val includedDeleted get() = myIncludedChanges.count(DELETED_FILTER)
  override var includedUnversioned: Int = 0
    private set

  @JvmOverloads
  fun update(displayedChanges: List<Change>,
             includedChanges: List<Change>,
             unversionedFilesCount: Int = 0,
             includedUnversionedFilesCount: Int = 0) {
    myDisplayedChanges = displayedChanges
    myIncludedChanges = includedChanges
    unversioned = unversionedFilesCount
    includedUnversioned = includedUnversionedFilesCount
  }
}
