// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.frontend.shelf.tree

import com.intellij.openapi.project.Project
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.impl.frontend.changes.ChangesTree
import com.intellij.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class ShelfTree(project: Project, private val treeRoot: ChangesBrowserRootNode) : ChangesTree(project, false, false) {

  override fun isPathEditable(path: TreePath): Boolean {
    return isEditable && selectionCount == 1 && path.lastPathComponent is ShelvedChangeListNode
  }

  fun rebuildTree() {
    updateTreeModel(DefaultTreeModel(treeRoot))
  }


  fun getSelectedLists(): Set<ShelvedChangeListEntity> {
    return selectionPaths?.mapNotNull { TreeUtil.findObjectInPath(it, ShelvedChangeListEntity::class.java) }?.toSet() ?: emptySet()
  }


}