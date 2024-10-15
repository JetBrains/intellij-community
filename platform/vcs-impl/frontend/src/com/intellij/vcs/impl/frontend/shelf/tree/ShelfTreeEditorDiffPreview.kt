// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.frontend.shelf.tree

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.asEntity
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.impl.frontend.changes.ChangesTreeEditorDiffPreview
import com.intellij.vcs.impl.frontend.changes.SelectedData
import com.intellij.vcs.impl.frontend.shelf.subscribeToShelfTreeSelectionChanged
import com.intellij.vcs.impl.shared.rhizome.SelectShelveChangeEntity
import com.intellij.vcs.impl.shared.rhizome.ShelvedChangeEntity
import com.intellij.vcs.impl.shared.rpc.ChangeListDto
import com.intellij.vcs.impl.shared.rpc.RemoteShelfApi
import fleet.kernel.sharedRef
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.swing.tree.DefaultMutableTreeNode

class ShelfTreeEditorDiffPreview(tree: ShelfTree, private val cs: CoroutineScope, private val project: Project) : ChangesTreeEditorDiffPreview<ShelfTree>(tree) {

  init {
    subscribeToShelfTreeSelectionChanged(project, cs, ::selectNodeInTree)
    trackTreeSelection()
  }

  private fun trackTreeSelection() {
    tree.addTreeSelectionListener {
      cs.launch {
        withKernel {
          val changeListDto = creteSelectedListsDto() ?: return@withKernel
          RemoteApiProviderService.resolve(remoteApiDescriptor<RemoteShelfApi>()).notifyNodeSelected(project.asEntity().sharedRef(), changeListDto)
        }
      }
    }
  }

  override fun performDiffAction(): Boolean {
    cs.launch {
      withKernel {
        val changeListDto = creteSelectedListsDto() ?: return@withKernel
        RemoteApiProviderService.resolve(remoteApiDescriptor<RemoteShelfApi>()).showDiffForChanges(project.asEntity().sharedRef(), changeListDto)
      }
    }
    return true
  }

  private fun creteSelectedListsDto(): ChangeListDto? {
    val selectedLists = tree.getSelectedLists()
    if (selectedLists.size != 1) return null
    val selectedShelvedChanges = SelectedData(tree).iterateUserObjects(ShelvedChangeEntity::class.java).map { it.sharedRef() }
    return ChangeListDto(selectedLists.first().sharedRef(), selectedShelvedChanges.toList())
  }

  private fun selectNodeInTree(it: SelectShelveChangeEntity) {
    val rootNode = tree.model.root as DefaultMutableTreeNode
    val changeListNode = TreeUtil.findNodeWithObject(rootNode, it.changeList) ?: return
    val changeNode = TreeUtil.findNodeWithObject(changeListNode, it.change) ?: return
    cs.launch(Dispatchers.EDT) {
      TreeUtil.selectPath(tree, TreeUtil.getPathFromRoot(changeNode), false)
    }
  }
}