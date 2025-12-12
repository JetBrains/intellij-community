// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.ide.dnd.DnDEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.createNameForChangeList
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun ChangesTree.installDndWithShelvesSupport(disposable: Disposable) {
  ChangesViewDnDWithShelvesSupport(this).install(disposable)
}

private class ChangesViewDnDWithShelvesSupport(tree: ChangesTree) : ChangesViewDnDSupport(tree) {
  override fun canHandleDropEvent(aEvent: DnDEvent, dropNode: ChangesBrowserNode<*>?): Boolean {
    val attached = aEvent.attachedObject
    return (attached is ShelvedChangeListDragBean && (dropNode == null || dropNode is ChangesBrowserChangeListNode))
           || super.canHandleDropEvent(aEvent, dropNode)
  }

  override fun drop(aEvent: DnDEvent) {
    val attached = aEvent.attachedObject
    if (attached !is ShelvedChangeListDragBean) {
      return super.drop(aEvent)
    }

    val dropRootNode = getDropRootNode(myTree, aEvent)
    val project = myTree.project
    val targetChangeList = if (dropRootNode != null) {
      TreeUtil.getUserObject(LocalChangeList::class.java, dropRootNode)
    }
    else {
      val changeList = attached.shelvedChangelists.firstOrNull()
      val suggestedName = changeList?.name ?: VcsBundle.message("changes.new.changelist")
      val listName = createNameForChangeList(project, suggestedName)
      ChangeListManager.getInstance(project).addChangeList(listName, null)
    }
    ShelveChangesManager.unshelveSilentlyWithDnd(project, attached, targetChangeList, !isCopyAction(aEvent))
  }
}
