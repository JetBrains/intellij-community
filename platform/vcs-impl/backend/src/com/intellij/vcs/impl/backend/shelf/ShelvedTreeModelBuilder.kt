// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf;

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFile
import com.intellij.openapi.vcs.changes.shelf.ShelvedChange
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import com.intellij.openapi.vcs.changes.shelf.ShelvedWrapper
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import org.jetbrains.annotations.ApiStatus
import java.util.ArrayList
import java.util.Comparator
import java.util.function.Function

@ApiStatus.Internal
class ShelvedTreeModelBuilder(project: Project?, grouping: ChangesGroupingPolicyFactory) : TreeModelBuilder(project, grouping) {
  fun setShelvedLists(shelvedLists: MutableList<ShelvedChangeList>) {
    createShelvedListsWithChangesNode(shelvedLists, myRoot)
  }

  fun setDeletedShelvedLists(shelvedLists: MutableList<ShelvedChangeList>) {
    createShelvedListsWithChangesNode(shelvedLists, createTagNode(VcsBundle.message("shelve.recently.deleted.node")))
  }

  private fun createShelvedListsWithChangesNode(
    shelvedLists: MutableList<ShelvedChangeList>,
    parentNode: ChangesBrowserNode<*>
  ) {
    for (changeList in shelvedLists) {
      val shelvedListNode = ShelvedListNode(changeList)
      insertSubtreeRoot(shelvedListNode, parentNode)

      val changes = changeList.getChanges()
      if (changes == null) continue

      val shelvedChanges: MutableList<ShelvedWrapper> = ArrayList<ShelvedWrapper>()
      changes.stream().map<ShelvedWrapper?> { change: ShelvedChange? ->
        ShelvedWrapper(change, changeList)
      }.forEach { e: ShelvedWrapper? -> shelvedChanges.add(e!!) }
      changeList.getBinaryFiles().stream().map<ShelvedWrapper?> { binaryChange: ShelvedBinaryFile? ->
        ShelvedWrapper(binaryChange, changeList)
      }.forEach { e: ShelvedWrapper? -> shelvedChanges.add(e!!) }

      shelvedChanges.sort(Comparator.comparing<ShelvedWrapper?, Change?>(Function { s: ShelvedWrapper? -> s.getChangeWithLocal(myProject) },
                                                                         CHANGE_COMPARATOR))

      for (shelved in shelvedChanges) {
        val change = shelved.getChangeWithLocal(myProject)
        val filePath = ChangesUtil.getFilePath(change)
        insertChangeNode(change, shelvedListNode,
                         ShelvedChangeNode(shelved, filePath, change.getOriginText(myProject)))
      }
    }
  }
}
