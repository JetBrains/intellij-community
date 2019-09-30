// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.util.DiffPlaces
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.*
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.util.ui.tree.TreeUtil
import java.util.stream.Stream
import kotlin.streams.asSequence

private fun wrap(changes: Stream<Change>, unversioned: Stream<FilePath>): List<Wrapper> =
  (changes.asSequence().map { ChangeWrapper(it) } +
   unversioned.asSequence().mapNotNull { it.virtualFile }.map { UnversionedFileWrapper(it) }
  ).toList()

private class ChangesViewDiffPreviewProcessor(private val changesView: ChangesListView) :
  ChangeViewDiffRequestProcessor(changesView.project, DiffPlaces.CHANGES_VIEW) {

  init {
    putContextUserData(DiffUserDataKeysEx.LAST_REVISION_WITH_LOCAL, true)
  }

  override fun getSelectedChanges(): List<Wrapper> =
    if (changesView.isSelectionEmpty) allChanges
    else wrap(changesView.selectedChanges, changesView.selectedUnversionedFiles)

  override fun getAllChanges(): List<Wrapper> = wrap(changesView.changes, changesView.unversionedFiles)

  override fun hasSelection(): Boolean {
    return !changesView.isSelectionEmpty
  }

  override fun selectChange(change: Wrapper) {
    changesView.findNodePathInTree(change.userObject)?.let { TreeUtil.selectPath(changesView, it, false) }
  }
}