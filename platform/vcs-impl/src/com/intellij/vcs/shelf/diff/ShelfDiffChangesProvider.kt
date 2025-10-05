// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.shelf.diff

import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.Wrapper
import com.intellij.openapi.vcs.changes.shelf.ShelvedWrapper
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.util.containers.JBIterable
import com.intellij.vcs.shelf.ShelvedListNode
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.TreeModel

@ApiStatus.Internal
class ShelfDiffChangesProvider(var treeModel: TreeModel) {
  val changesStateFlow: MutableStateFlow<ShelfDiffChangesState> = MutableStateFlow(ShelfDiffChangesState())

  fun getSelectedChanges(): JBIterable<Wrapper> {
    return JBIterable.from(changesStateFlow.value.selectedChanges)
  }

  fun getAllChanges(): JBIterable<Wrapper> {
    val model = treeModel
    val changeLists = changesStateFlow.value.selectedChanges.map { (it as ShelvedWrapper).changeList }.toSet()

    return VcsTreeModelData.all(model).iterateRawNodes()
      .filter { it is ShelvedListNode && changeLists.contains(it.changeList) }
      .flatMap { VcsTreeModelData.allUnder(it).iterateUserObjects(ShelvedWrapper::class.java) }
  }
}

@ApiStatus.Internal
class ShelfDiffChangesState(val selectedChanges: List<Wrapper> = emptyList(), val fromModelChange: Boolean = false)