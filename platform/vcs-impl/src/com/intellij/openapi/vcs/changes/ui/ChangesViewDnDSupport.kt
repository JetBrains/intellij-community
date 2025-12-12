// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.ide.dnd.DnDActionInfo
import com.intellij.ide.dnd.DnDDragStartBean
import com.intellij.ide.dnd.DnDEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.createNameForChangeList
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.platform.vcs.impl.shared.changes.ChangeListDnDSupport.Companion.getInstance
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ChangesViewDnDSupport private constructor(private val myProject: Project, tree: ChangesTree) : ChangesTreeDnDSupport(tree) {
  override fun createDragStartBean(info: DnDActionInfo): DnDDragStartBean? {
    if (info.isMove) {
      val changes = VcsTreeModelData.selected(myTree)
        .iterateUserObjects(Change::class.java).toList()
      val unversionedFiles = VcsTreeModelData.selectedUnderTag(myTree, ChangesBrowserNode.UNVERSIONED_FILES_TAG)
        .iterateUserObjects(FilePath::class.java).toList()
      val ignoredFiles = VcsTreeModelData.selectedUnderTag(myTree, ChangesBrowserNode.IGNORED_FILES_TAG)
        .iterateUserObjects(FilePath::class.java).toList()

      if (!changes.isEmpty() || !unversionedFiles.isEmpty() || !ignoredFiles.isEmpty()) {
        return DnDDragStartBean(ChangeListDragBean(myTree, changes, unversionedFiles, ignoredFiles))
      }
    }

    return null
  }

  override fun canHandleDropEvent(aEvent: DnDEvent, dropNode: ChangesBrowserNode<*>?): Boolean {
    val attached = aEvent.getAttachedObject()
    if (attached is ChangeListDragBean) {
      if (dropNode != null) {
        attached.targetNode = dropNode
        return attached.sourceComponent === myTree && dropNode.canAcceptDrop(attached)
      }
    }
    else if (attached is ShelvedChangeListDragBean) {
      return dropNode == null || dropNode is ChangesBrowserChangeListNode
    }
    return false
  }

  override fun drop(aEvent: DnDEvent) {
    val attached = aEvent.getAttachedObject()
    if (attached is ShelvedChangeListDragBean) {
      val dropRootNode = getDropRootNode(myTree, aEvent)
      val targetChangeList = if (dropRootNode != null) {
        TreeUtil.getUserObject(LocalChangeList::class.java, dropRootNode)
      }
      else {
        val changeList = attached.shelvedChangelists.firstOrNull()
        val suggestedName = changeList?.name ?: VcsBundle.message("changes.new.changelist")
        val listName = createNameForChangeList(myProject, suggestedName)
        ChangeListManager.getInstance(myProject).addChangeList(listName, null)
      }
      ShelveChangesManager.unshelveSilentlyWithDnd(myProject, attached, targetChangeList, !isCopyAction(aEvent))
    }
    else if (attached is ChangeListDragBean) {
      val changesBrowserNode = attached.targetNode
      if (changesBrowserNode != null) {
        changesBrowserNode.acceptDrop(getInstance(myProject), attached)
      }
    }
  }

  companion object {
    fun install(project: Project, tree: ChangesTree, disposable: Disposable) {
      ChangesViewDnDSupport(project, tree).install(disposable)
    }
  }
}