// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.ide.dnd.DnDActionInfo
import com.intellij.ide.dnd.DnDDragStartBean
import com.intellij.ide.dnd.DnDEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.platform.vcs.impl.shared.changes.ChangeListsViewModel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun ChangesTree.installDndSupport(disposable: Disposable) {
  ChangesViewDnDSupport(this).install(disposable)
}

@ApiStatus.Internal
open class ChangesViewDnDSupport(tree: ChangesTree) : ChangesTreeDnDSupport(tree) {
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
    return false
  }

  override fun drop(aEvent: DnDEvent) {
    val attached = aEvent.getAttachedObject()
    if (attached is ChangeListDragBean) {
      val changesBrowserNode = attached.targetNode
      if (changesBrowserNode != null) {
        changesBrowserNode.acceptDrop(ChangeListsViewModel.getInstance(myTree.project), attached)
      }
    }
  }
}