// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangesTreeCompatibilityProvider
import org.jetbrains.annotations.Nls

class ChangesBrowserUnversionedFilesNode(private val project: Project,
                                         files: List<FilePath>)
  : ChangesBrowserSpecificFilePathsNode<ChangesBrowserNode.Tag>(UNVERSIONED_FILES_TAG, files,
                                                                { ChangesTreeCompatibilityProvider.getInstance().showUnversionedViewDialog(project) }) {

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    super.render(renderer, selected, expanded, hasFocus)
    if (!project.isDisposed && ChangesTreeCompatibilityProvider.getInstance().isUnversionedInUpdateMode(project)) {
      appendUpdatingState(renderer)
    }
  }

  @Nls
  override fun getTextPresentation(): String = getUserObject().toString() //NON-NLS

  override fun getSortWeight(): Int = UNVERSIONED_SORT_WEIGHT
}