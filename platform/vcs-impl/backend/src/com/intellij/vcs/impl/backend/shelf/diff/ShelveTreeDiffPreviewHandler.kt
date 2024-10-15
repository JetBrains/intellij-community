// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf.diff

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.shelf.ShelvedWrapper
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.ChangesTreeDiffPreviewHandler
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.asEntity
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.impl.backend.shelf.ShelfTree
import com.intellij.vcs.impl.backend.shelf.ShelfTreeHolder.Companion.ENTITY_ID_KEY
import com.intellij.vcs.impl.backend.shelf.ShelvedListNode
import com.intellij.vcs.impl.shared.rhizome.SelectShelveChangeEntity
import com.intellij.vcs.impl.shared.rhizome.ShelvedChangeEntity
import com.intellij.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import fleet.kernel.change
import fleet.kernel.shared
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class ShelveTreeDiffPreviewHandler(private val project: Project, private val cs: CoroutineScope) : ChangesTreeDiffPreviewHandler() {
  override fun iterateSelectedChanges(tree: ChangesTree): Iterable<ChangeViewDiffRequestProcessor.Wrapper> {
    return (tree as ShelfTree).selectedChanges
  }

  override fun iterateAllChanges(tree: ChangesTree): Iterable<ChangeViewDiffRequestProcessor.Wrapper> {
    val changeLists = iterateSelectedChanges(tree).map { (it as ShelvedWrapper).changeList }.toSet()

    return VcsTreeModelData.all(tree).iterateRawNodes()
      .filter { it is ShelvedListNode && changeLists.contains(it.changeList) }
      .flatMap { VcsTreeModelData.allUnder(it).iterateUserObjects(ShelvedWrapper::class.java) }
  }

  override fun selectChange(tree: ChangesTree, change: ChangeViewDiffRequestProcessor.Wrapper) {
    if (change is ShelvedWrapper) {
      cs.launch {
        withKernel {
          val nodeToSelect = TreeUtil.findNodeWithObject(tree.model.root as ChangesBrowserNode<*>, change) as? ChangesBrowserNode<*>
                             ?: return@withKernel
          val changeListNode = nodeToSelect.path.firstOrNull { it is ShelvedListNode } as ChangesBrowserNode<*>
          val changeEntity = nodeToSelect.getUserData(ENTITY_ID_KEY)?.derefOrNull() as ShelvedChangeEntity
          val changeListEntity = changeListNode.getUserData(ENTITY_ID_KEY)?.derefOrNull() as ShelvedChangeListEntity
          change {
            shared {
              SelectShelveChangeEntity.new {
                it[SelectShelveChangeEntity.Change] = changeEntity
                it[SelectShelveChangeEntity.ChangeList] = changeListEntity
                it[SelectShelveChangeEntity.Project] = project.asEntity()
              }
            }
          }
        }
      }
    }
  }
}
