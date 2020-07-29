/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ide.DataManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangeListOwner
import com.intellij.openapi.vcs.changes.IgnoredViewDialog
import com.intellij.openapi.vcs.changes.VcsIgnoreManagerImpl
import com.intellij.openapi.vcs.changes.ignore.actions.IgnoreFileActionGroup
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.treeStructure.Tree
import com.intellij.vcsUtil.VcsUtil
import javax.swing.tree.TreePath


class ChangesBrowserIgnoredFilesNode(val project: Project,
                                     files: List<FilePath>,
                                     private val myUpdatingMode: Boolean) : ChangesBrowserSpecificFilePathsNode<Any>(
  ChangesBrowserNode.IGNORED_FILES_TAG, files, { if (!project.isDisposed) IgnoredViewDialog(project).show() }) {

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    super.render(renderer, selected, expanded, hasFocus)
    if (myUpdatingMode) {
      appendUpdatingState(renderer)
    }
  }

  override fun canAcceptDrop(dragBean: ChangeListDragBean) = dragBean.unversionedFiles.isNotEmpty()

  override fun acceptDrop(dragOwner: ChangeListOwner, dragBean: ChangeListDragBean) {
    val tree = dragBean.sourceComponent as? Tree ?: return

    val vcs = dragBean.unversionedFiles.getVcs() ?: return

    val ignoreFileType = VcsIgnoreManagerImpl.getInstanceImpl(project).findIgnoreFileType(vcs) ?: return
    val ignoreGroup = IgnoreFileActionGroup(ignoreFileType)

    val popup = JBPopupFactory.getInstance().createActionGroupPopup(
      null, ignoreGroup, DataManager.getInstance().getDataContext(dragBean.sourceComponent),
      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false)
    tree.getPathBounds(TreePath(dragBean.targetNode.path))?.let { dropBounds ->
      popup.show(RelativePoint(dragBean.sourceComponent, dropBounds.location))
    }
  }

  override fun getSortWeight() = ChangesBrowserNode.IGNORED_SORT_WEIGHT

  private fun List<FilePath>.getVcs(): AbstractVcs? = mapNotNull { file -> VcsUtil.getVcsFor(project, file) }.firstOrNull()
}