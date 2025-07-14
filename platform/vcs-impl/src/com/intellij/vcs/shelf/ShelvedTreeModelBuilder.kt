// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.shelf

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import com.intellij.openapi.vcs.changes.shelf.ShelvedWrapper
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.TagChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ShelvedTreeModelBuilder(val project: Project, grouping: ChangesGroupingPolicyFactory) : TreeModelBuilder(project, grouping) {
  fun setShelvedLists(shelvedLists: List<ShelvedChangeList>) {
    createShelvedListsWithChangesNode(shelvedLists, myRoot)
  }

  fun setDeletedShelvedLists(shelvedLists: List<ShelvedChangeList>) {
    val tag = ChangesBrowserNode.TagImpl(VcsBundle.message("shelve.recently.deleted.node"))
    val parentNode = insertTagNode(RecentlyDeletedNode(tag, SimpleTextAttributes.REGULAR_ATTRIBUTES, true))
    createShelvedListsWithChangesNode(shelvedLists, parentNode)
  }

  private fun createShelvedListsWithChangesNode(
    shelvedLists: List<ShelvedChangeList>,
    parentNode: ChangesBrowserNode<*>,
  ) {
    for (changeList in shelvedLists) {
      val shelvedListNode = ShelvedListNode(changeList)
      insertSubtreeRoot(shelvedListNode, parentNode)

      val changes = changeList.changes ?: continue

      val shelvedChanges: MutableList<ShelvedWrapper> = ArrayList<ShelvedWrapper>()
      changes.map { ShelvedWrapper(it, changeList) }
        .forEach { shelvedChanges.add(it) }
      changeList.binaryFiles.map { ShelvedWrapper(it, changeList) }
        .forEach { shelvedChanges.add(it) }

      shelvedChanges.sortWith(Comparator.comparing({ it.getChangeWithLocal(project) }, CHANGE_COMPARATOR))

      for (shelved in shelvedChanges) {
        val change = shelved.getChangeWithLocal(project)
        val filePath = ChangesUtil.getFilePath(change)
        insertChangeNode(change, shelvedListNode, ShelvedChangeNode(shelved, filePath, change.getOriginText(project)))
      }
    }
  }
}

@ApiStatus.Internal
class RecentlyDeletedNode(userObject: Tag, attributes: SimpleTextAttributes, expandByDefault: Boolean) :
  TagChangesBrowserNode(userObject, attributes, expandByDefault) {
  override fun getSortWeight(): Int {
    return DELETED_SORT_WEIGHT
  }
}