// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangesTreeCompatibilityProvider
import com.intellij.platform.vcs.impl.shared.changes.ChangeListsViewModel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

class ChangesBrowserUnversionedFilesNode : ChangesBrowserSpecificFilePathsNode<ChangesBrowserNode.Tag> {
  private val project: Project

  constructor(project: Project, files: List<FilePath>) :
    super(UNVERSIONED_FILES_TAG, files, { ChangesTreeCompatibilityProvider.getInstance().showUnversionedViewDialog(project) }) {
    this.project = project
  }

  @ApiStatus.Internal
  constructor(project: Project, fileCount: Int, directoryCount: Int) :
    super(UNVERSIONED_FILES_TAG, fileCount, directoryCount,
          { ChangesTreeCompatibilityProvider.getInstance().showUnversionedViewDialog(project) }) {
    this.project = project
  }

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    super.render(renderer, selected, expanded, hasFocus)
    if (!project.isDisposed && ChangeListsViewModel.getInstance(project).changeListManagerState.value.fileHoldersState.unversionedInUpdateMode) {
      appendUpdatingState(renderer)
    }
  }

  @Nls
  override fun getTextPresentation(): String = getUserObject().toString() //NON-NLS

  override fun getSortWeight(): Int = UNVERSIONED_SORT_WEIGHT
}
