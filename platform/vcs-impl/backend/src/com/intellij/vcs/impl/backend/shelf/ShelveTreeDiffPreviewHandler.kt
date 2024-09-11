// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf

import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.shelf.ShelvedWrapper
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.ChangesTreeDiffPreviewHandler
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.tree.DefaultMutableTreeNode

internal class ShelveTreeDiffPreviewHandler : ChangesTreeDiffPreviewHandler() {
  override fun iterateSelectedChanges(tree: ChangesTree): Iterable<@JvmWildcard ChangeViewDiffRequestProcessor.Wrapper> {
    return VcsTreeModelData.selected(tree).iterateUserObjects<ShelvedWrapper>(ShelvedWrapper::class.java)
  }

  override fun iterateAllChanges(tree: ChangesTree): Iterable<@JvmWildcard ChangeViewDiffRequestProcessor.Wrapper> {
    val changeLists = VcsTreeModelData.selected(tree).iterateUserObjects(ShelvedWrapper::class.java)
        .map{it.changeList }
        .toSet()

    return VcsTreeModelData.all(tree).iterateRawNodes()
      .filter { it is ShelvedListNode && changeLists.contains(it.changeList) }
      .flatMap { VcsTreeModelData.allUnder(it).iterateUserObjects(ShelvedWrapper::class.java) }
  }

  override fun selectChange(tree: ChangesTree, change: ChangeViewDiffRequestProcessor.Wrapper) {
    if (change is ShelvedWrapper) {
      val root: DefaultMutableTreeNode = tree.root
      val changelistNode = TreeUtil.findNodeWithObject(root, change.changeList)
      if (changelistNode == null) return

      val node = TreeUtil.findNodeWithObject(changelistNode, change)
      if (node == null) return
      TreeUtil.selectPath(tree, TreeUtil.getPathFromRoot(node), false)
    }
  }

  companion object {
    val INSTANCE: ShelveTreeDiffPreviewHandler = ShelveTreeDiffPreviewHandler()
  }
}
