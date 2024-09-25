// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.frontend.shelf.tree

import com.intellij.platform.kernel.withKernel
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.vcs.impl.frontend.changes.ChangesTreeEditorDiffPreview
import com.intellij.vcs.impl.frontend.changes.SelectedData
import com.intellij.vcs.impl.shared.rhizome.ShelvedChangeEntity
import com.intellij.vcs.impl.shared.rpc.ChangeListDto
import com.intellij.vcs.impl.shared.rpc.RemoteShelfApi
import fleet.kernel.sharedRef
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ShelfTreeEditorDiffPreview(tree: ShelfTree, val cs: CoroutineScope) : ChangesTreeEditorDiffPreview<ShelfTree>(tree) {

  override fun performDiffAction(): Boolean {
    val selectedLists = tree.getSelectedLists()
    if (selectedLists.size != 1) return false
    cs.launch {
      withKernel {
        val selectedShelvedChanges = SelectedData(tree).iterateUserObjects(ShelvedChangeEntity::class.java).map { it.sharedRef() }
        val changeListDto = ChangeListDto(selectedLists.first().sharedRef(), selectedShelvedChanges.toList())
        cs.launch {
          RemoteApiProviderService.resolve(remoteApiDescriptor<RemoteShelfApi>()).showDiffForChanges(1, changeListDto)
        }
      }
    }
    return true
  }

}