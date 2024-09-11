// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf;

import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.shelf.*;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NavigatableAdapter;
import com.intellij.ui.TreeSpeedSearch;

import javax.swing.tree.TreePath;
import java.util.ArrayList;

import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager.HELP_ID
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager.SHELVED_CHANGES_TREE
import com.intellij.openapi.vcs.changes.shelf.ShelvedWrapper
import com.intellij.openapi.vcs.changes.ui.AsyncChangesTree
import com.intellij.openapi.vcs.changes.ui.AsyncChangesTreeModel
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.util.containers.toArray
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ShelfTree internal constructor(project: Project) : AsyncChangesTree(project, false, false, false) {
  override val changesTreeModel: AsyncChangesTreeModel = ShelfTreeAsyncModel(project, scope)
  private val deleteProvider: DeleteProvider = ShelveDeleteProvider(myProject, this)

  init {
    TreeSpeedSearch.installOn(this, true, ChangesBrowserNode.TO_TEXT_CONVERTER)
    isKeepTreeState = true
  }

  override fun isPathEditable(path: TreePath): Boolean {
    return isEditable && selectionCount == 1 && path.lastPathComponent is ShelvedListNode
  }

  override fun installGroupingSupport(): ChangesGroupingSupport {
    val groupingSupport = ChangesGroupingSupport(myProject, this, false)
    installGroupingSupport(this, groupingSupport,
                           { ShelveChangesManager.getInstance(myProject).grouping },
                           { ShelveChangesManager.getInstance(myProject).grouping = it })
    return groupingSupport
  }

  override fun getToggleClickCount(): Int {
    return 2
  }

  override fun uiDataSnapshot(sink: DataSink) {
    super.uiDataSnapshot(sink)
    sink[SHELVED_CHANGES_TREE] = this
    sink[ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY] = getSelectedLists { !it.isRecycled && !it.isDeleted }.toList()
    sink[ShelvedChangesViewManager.SHELVED_RECYCLED_CHANGELIST_KEY] = getSelectedLists { it.isRecycled && !it.isDeleted }.toList()
    sink[ShelvedChangesViewManager.SHELVED_DELETED_CHANGELIST_KEY] = getSelectedLists { it.isDeleted }.toList()
    sink[ShelvedChangesViewManager.SHELVED_CHANGE_KEY] = VcsTreeModelData.selected(this)
      .iterateUserObjects(ShelvedWrapper::class.java)
      .filterMap { it.shelvedChange }
      .toList()
    sink[ShelvedChangesViewManager.SHELVED_BINARY_FILE_KEY] = VcsTreeModelData.selected(this)
      .iterateUserObjects(ShelvedWrapper::class.java)
      .filterMap { it.binaryFile }
      .toList()
    if (!isEditing()) {
      sink[PlatformDataKeys.DELETE_ELEMENT_PROVIDER] = deleteProvider
    }
    val shelvedChanges = VcsTreeModelData.selected(this).userObjects(ShelvedWrapper::class.java)
    if (!shelvedChanges.isEmpty()) {
      sink[VcsDataKeys.CHANGES] = shelvedChanges.map {
        it.getChangeWithLocal(myProject)
      }.toTypedArray()
    }
    sink.set<Array<Navigatable?>>(CommonDataKeys.NAVIGATABLE_ARRAY, getNavigatables(shelvedChanges)
      .toArray<Navigatable?>(Navigatable.EMPTY_NAVIGATABLE_ARRAY))
    sink.set<String>(PlatformCoreDataKeys.HELP_ID, HELP_ID)
  }

  private fun getNavigatables(shelvedChanges: MutableList<ShelvedWrapper>): MutableList<Navigatable?> {
    val navigatables = ArrayList<Navigatable?>()
    for (shelvedChange in shelvedChanges) {
      if (shelvedChange.beforePath != null && FileStatus.ADDED != shelvedChange.fileStatus) {
        val navigatable: NavigatableAdapter = object : NavigatableAdapter() {
          override fun navigate(requestFocus: Boolean) {
            val vf = shelvedChange.getBeforeVFUnderProject(myProject)
            if (vf != null) {
              navigate(myProject, vf, true)
            }
          }
        }
        navigatables.add(navigatable)
      }
    }
    return navigatables
  }

  private fun getSelectedLists(
    condition: (ShelvedChangeList) -> Boolean,
  ): Set<ShelvedChangeList> {
    return selectionPaths?.mapNotNull { TreeUtil.findObjectInPath(it, ShelvedChangeList::class.java) }
             ?.filter { condition(it) }?.toSet() ?: emptySet()
  }


  fun getSelectedChangesOrAll(dataContext: DataContext): ListSelection<ShelvedWrapper> {
    val tree = dataContext.getData<ChangesTree>(SHELVED_CHANGES_TREE) ?: return ListSelection.empty<ShelvedWrapper>()

    val wrappers = ListSelection.createAt<ShelvedWrapper>(
      VcsTreeModelData.selected(tree).userObjects<ShelvedWrapper>(ShelvedWrapper::class.java), 0)

    if (wrappers.getList().size == 1) {
      // return all changes for selected changelist
      val changeList = getSelectedLists { true }.firstOrNull()
      if (changeList != null) {
        val changeListNode = TreeUtil.findNodeWithObject(tree.root, changeList) as ChangesBrowserNode<*>?
        if (changeListNode != null) {
          val allWrappers = changeListNode.getAllObjectsUnder<ShelvedWrapper>(ShelvedWrapper::class.java)
          if (allWrappers.size > 1) {
            val toSelect = wrappers.list.first()
            return ListSelection.create<ShelvedWrapper>(allWrappers, toSelect)
          }
        }
      }
    }
    return wrappers.asExplicitSelection()
  }

  fun invalidateDataAndRefresh(onRefreshed: Runnable?) {
    (changesTreeModel as? ShelfTreeAsyncModel)?.invalidateData()
    requestRefresh(onRefreshed)
  }
}
