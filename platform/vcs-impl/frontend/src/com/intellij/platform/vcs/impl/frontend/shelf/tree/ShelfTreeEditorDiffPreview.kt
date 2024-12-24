// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.shelf.tree

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.sync.OverflowSemaphore
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.platform.vcs.impl.frontend.changes.ChangesTreeEditorDiffPreview
import com.intellij.platform.vcs.impl.frontend.changes.SelectedData
import com.intellij.platform.vcs.impl.frontend.shelf.subscribeToShelfTreeSelectionChanged
import com.intellij.platform.vcs.impl.shared.rhizome.SelectShelveChangeEntity
import com.intellij.platform.vcs.impl.shared.rhizome.ShelvedChangeEntity
import com.intellij.platform.vcs.impl.shared.rpc.ChangeListRpc
import com.intellij.platform.vcs.impl.shared.rpc.RemoteShelfApi
import fleet.kernel.ref
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.DefaultMutableTreeNode

@ApiStatus.Internal
class ShelfTreeEditorDiffPreview(tree: ShelfTree, private val cs: CoroutineScope, private val project: Project) : ChangesTreeEditorDiffPreview<ShelfTree>(tree) {

  private val selectionUpdateSemaphore = OverflowSemaphore(overflow = BufferOverflow.DROP_OLDEST)

  init {
    subscribeToShelfTreeSelectionChanged(project, cs, ::selectNodeInTree)
    trackTreeSelection()
  }

  private fun trackTreeSelection() {
    tree.addTreeSelectionListener {
      cs.launch(Dispatchers.IO) {
        selectionUpdateSemaphore.withPermit {
          withKernel {
            val changeListDto = creteSelectedListsDto() ?: return@withKernel
            RemoteShelfApi.getInstance().notifyNodeSelected(project.projectId(), changeListDto, false)
          }
        }
      }
    }
  }

  override fun performDiffAction(): Boolean {
    cs.launch(Dispatchers.IO) {
      withKernel {
        val changeListDto = creteSelectedListsDto() ?: return@withKernel
        RemoteShelfApi.getInstance().showDiffForChanges(project.projectId(), changeListDto)
      }
    }
    return true
  }

  private fun creteSelectedListsDto(): ChangeListRpc? {
    val selectedLists = tree.getSelectedLists()
    if (selectedLists.size != 1) return null
    val selectedShelvedChanges = SelectedData(tree).iterateUserObjects(ShelvedChangeEntity::class.java).map { it.ref() }
    return ChangeListRpc(selectedLists.first().ref(), selectedShelvedChanges.toList())
  }

  private fun selectNodeInTree(it: SelectShelveChangeEntity) {
    val rootNode = tree.model.root as DefaultMutableTreeNode
    val changeListNode = TreeUtil.findNodeWithObject(rootNode, it.changeList) ?: return
    val nodeToSelect = it.change?.let { change ->
      TreeUtil.findNodeWithObject(changeListNode, change)
    } ?: changeListNode

    cs.launch(Dispatchers.EDT) {
      TreeUtil.selectPath(tree, TreeUtil.getPathFromRoot(nodeToSelect), false)
    }
  }
}