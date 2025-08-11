// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangeListOwner
import com.intellij.openapi.vcs.changes.ChangesTreeCompatibilityProvider
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class ChangesBrowserIgnoredFilesNode(private val project: Project,
                                     files: List<FilePath>)
  : ChangesBrowserSpecificFilePathsNode<ChangesBrowserNode.Tag>(ChangesBrowserNode.IGNORED_FILES_TAG, files,
                                                                { ChangesTreeCompatibilityProvider.getInstance().showIgnoredViewDialog(project) }) {

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    super.render(renderer, selected, expanded, hasFocus)
    if (!project.isDisposed && ChangesTreeCompatibilityProvider.getInstance().isIgnoredInUpdateMode(project)) {
      appendUpdatingState(renderer)
    }
  }

  @ApiStatus.Internal
  override fun canAcceptDrop(dragBean: ChangeListDragBean) = dragBean.unversionedFiles.isNotEmpty()

  @ApiStatus.Internal
  override fun acceptDrop(dragOwner: ChangeListOwner, dragBean: ChangeListDragBean) {
    ChangesTreeCompatibilityProvider.getInstance().acceptIgnoredFilesDrop(project, dragOwner, dragBean)
  }

  @Nls
  override fun getTextPresentation(): String = getUserObject().toString() //NON-NLS

  override fun getSortWeight(): Int = ChangesBrowserNode.IGNORED_SORT_WEIGHT
}