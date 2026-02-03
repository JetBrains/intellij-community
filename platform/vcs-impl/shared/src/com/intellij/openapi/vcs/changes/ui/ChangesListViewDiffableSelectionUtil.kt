// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.ListSelection
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListChange
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ChangesListViewDiffableSelectionUtil {
  fun computeSelectionForDiff(changesView: ChangesListView): ListSelection<DiffSource> {
    val changes = changesView.selectedChanges.toList()
    val unversioned = changesView.selectedUnversionedFiles.toList()

    if (changes.size == 1 && unversioned.isEmpty()) { // show all changes from this changelist
      val selectedChange: Change = changes.first()
      var selectedChanges = changesView.getAllChangesFromSameChangelist(selectedChange)
      if (selectedChanges == null) {
        selectedChanges = changesView.getAllChangesFromSameAmendNode(selectedChange)
      }
      if (selectedChanges != null) {
        var selectedIndex = selectedChanges.indexOfFirst { ChangeListChange.HASHING_STRATEGY.equals(selectedChange, it) }
        if (selectedIndex == -1) selectedIndex = selectedChanges.indexOf(selectedChange)
        return ListSelection.createAt(selectedChanges, selectedIndex).map { DiffSource.Change(it) }
      }
    }

    if (unversioned.size == 1 && changes.isEmpty()) { // show all unversioned changes
      val selectedFile = unversioned.first()
      val allUnversioned = changesView.unversionedFiles.toList()
      val selectedIndex = allUnversioned.indexOf(selectedFile)
      return ListSelection.createAt(allUnversioned, selectedIndex).map { DiffSource.UnversionedFile(it) }
    }

    val changesSelection = ListSelection.createAt(changes, 0).map { DiffSource.Change(it) }
    val unversionedSelection = ListSelection.createAt(unversioned, 0).map { DiffSource.UnversionedFile(it) }
    return ListSelection.createAt(
      ContainerUtil.concat(changesSelection.getList(), unversionedSelection.getList()),
      0
    ).asExplicitSelection()
  }
}

@ApiStatus.Internal
sealed interface DiffSource {
  @JvmInline
  value class Change(val change: com.intellij.openapi.vcs.changes.Change) : DiffSource

  @JvmInline
  value class UnversionedFile(val filePath: com.intellij.openapi.vcs.FilePath) : DiffSource
}