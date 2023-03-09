/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.UnversionedViewDialog
import org.jetbrains.annotations.Nls

class ChangesBrowserUnversionedFilesNode(private val project: Project,
                                         files: List<FilePath>)
  : ChangesBrowserSpecificFilePathsNode<ChangesBrowserNode.Tag>(UNVERSIONED_FILES_TAG, files,
                                                                { if (!project.isDisposed) UnversionedViewDialog(project).show() }) {

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    super.render(renderer, selected, expanded, hasFocus)
    if (!project.isDisposed && ChangeListManagerImpl.getInstanceImpl(project).isUnversionedInUpdateMode) {
      appendUpdatingState(renderer)
    }
  }

  @Nls
  override fun getTextPresentation(): String = getUserObject().toString() //NON-NLS

  override fun getSortWeight(): Int = UNVERSIONED_SORT_WEIGHT
}