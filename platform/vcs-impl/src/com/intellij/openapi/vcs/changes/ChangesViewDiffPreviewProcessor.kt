// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.util.DiffPlaces
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.*
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.util.ui.tree.TreeUtil
import java.util.*
import java.util.stream.Stream

private fun wrap(changes: Stream<Change>, unversioned: Stream<FilePath>): Stream<Wrapper> =
  Stream.concat(
    changes.map { ChangeWrapper(it) },
    unversioned.map { it.virtualFile }.filter(Objects::nonNull).map { UnversionedFileWrapper(it!!) }
  )

private class ChangesViewDiffPreviewProcessor(private val changesView: ChangesListView) :
  ChangeViewDiffRequestProcessor(changesView.project, DiffPlaces.CHANGES_VIEW) {

  init {
    putContextUserData(DiffUserDataKeysEx.LAST_REVISION_WITH_LOCAL, true)
  }

  override fun getSelectedChanges(): Stream<Wrapper> =
    if (changesView.isSelectionEmpty) allChanges
    else wrap(changesView.selectedChanges, changesView.selectedUnversionedFiles)

  override fun getAllChanges(): Stream<Wrapper> = wrap(changesView.changes, changesView.unversionedFiles)

  override fun selectChange(change: Wrapper) {
    changesView.findNodePathInTree(change.userObject)?.let { TreeUtil.selectPath(changesView, it, false) }
  }
}