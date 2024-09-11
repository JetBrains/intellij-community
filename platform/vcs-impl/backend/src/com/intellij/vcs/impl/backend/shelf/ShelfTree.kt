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
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.shelf.*;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NavigatableAdapter;
import com.intellij.ui.TreeSpeedSearch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.map2Array;

import com.intellij.ide.DeleteProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFile
import com.intellij.openapi.vcs.changes.shelf.ShelvedChange
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager
import com.intellij.openapi.vcs.changes.shelf.ShelvedWrapper
import com.intellij.openapi.vcs.changes.ui.AsyncChangesTree
import com.intellij.openapi.vcs.changes.ui.AsyncChangesTreeModel
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.pom.Navigatable
import com.intellij.pom.NavigatableAdapter
import com.intellij.ui.TreeSpeedSearch
import com.intellij.util.Function
import com.intellij.util.containers.ContainerUtil
import java.util.ArrayList
import java.util.function.Consumer
import java.util.function.Supplier
import javax.swing.tree.TreePath

internal class ShelfTree private constructor(project: Project) : AsyncChangesTree(project, false, false, false) {
  private val myDeleteProvider: DeleteProvider = MyShelveDeleteProvider(myProject, this)
  private val myAsyncTreeModel: ShelfTreeAsyncModel

  init {
    myAsyncTreeModel = ShelfTreeAsyncModel(project, scope)

    TreeSpeedSearch.installOn(this, true, ChangesBrowserNode.TO_TEXT_CONVERTER)
    setKeepTreeState(true)
  }

  override fun getChangesTreeModel(): AsyncChangesTreeModel {
    return myAsyncTreeModel
  }

  override fun isPathEditable(path: TreePath): Boolean {
    return isEditable() && getSelectionCount() == 1 && path.getLastPathComponent() is ShelvedListNode
  }

  override fun installGroupingSupport(): ChangesGroupingSupport {
    val groupingSupport = ChangesGroupingSupport(myProject, this, false)
    installGroupingSupport(this, groupingSupport,
                           Supplier { ShelveChangesManager.getInstance(myProject).getGrouping() },
                           Consumer { newGrouping: MutableCollection<String?>? ->
                             ShelveChangesManager.getInstance(myProject).setGrouping(newGrouping!!)
                           })
    return groupingSupport
  }

  override fun getToggleClickCount(): Int {
    return 2
  }

  override fun uiDataSnapshot(sink: DataSink) {
    super.uiDataSnapshot(sink)
    sink.set<ChangesTree>(ShelvedChangesViewManager.SHELVED_CHANGES_TREE, this)
    sink.set<T>(ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY, ArrayList<Any?>(
      ShelvedChangesViewManager.getSelectedLists(this, { l -> !l.isRecycled() && !l.isDeleted() })))
    sink.set<T>(ShelvedChangesViewManager.SHELVED_RECYCLED_CHANGELIST_KEY, ArrayList<Any?>(
      ShelvedChangesViewManager.getSelectedLists(this, { l -> l.isRecycled() && !l.isDeleted() })))
    sink.set<T>(ShelvedChangesViewManager.SHELVED_DELETED_CHANGELIST_KEY,
                ArrayList<Any?>(ShelvedChangesViewManager.getSelectedLists(this, { l -> l.isDeleted() })))
    sink.set<MutableList<ShelvedChange?>>(ShelvedChangesViewManager.SHELVED_CHANGE_KEY,
                                          VcsTreeModelData.selected(this).iterateUserObjects<ShelvedWrapper?>(ShelvedWrapper::class.java)
                                            .filterMap<ShelvedChange?>(Function { s: ShelvedWrapper? -> s!!.getShelvedChange() })
                                            .toList())
    sink.set<MutableList<ShelvedBinaryFile?>>(ShelvedChangesViewManager.SHELVED_BINARY_FILE_KEY,
                                              VcsTreeModelData.selected(this).iterateUserObjects<ShelvedWrapper?>(
                                                ShelvedWrapper::class.java)
                                                .filterMap<ShelvedBinaryFile?>(Function { s: ShelvedWrapper? -> s!!.getBinaryFile() })
                                                .toList())
    if (!isEditing()) {
      sink.set<DeleteProvider>(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, myDeleteProvider)
    }
    val shelvedChanges = VcsTreeModelData.selected(this).userObjects<ShelvedWrapper?>(ShelvedWrapper::class.java)
    if (!shelvedChanges.isEmpty()) {
      sink.set<Array<Change?>>(VcsDataKeys.CHANGES, ContainerUtil.map2Array<ShelvedWrapper?, Change?>(shelvedChanges, Change::class.java,
                                                                                                      Function { s: ShelvedWrapper? ->
                                                                                                        s.getChangeWithLocal(myProject)
                                                                                                      }))
    }
    sink.set<Array<Navigatable?>>(CommonDataKeys.NAVIGATABLE_ARRAY, getNavigatables(shelvedChanges)
      .toArray<Navigatable?>(Navigatable.EMPTY_NAVIGATABLE_ARRAY))
    sink.set<String>(PlatformCoreDataKeys.HELP_ID, ShelvedChangesViewManager.HELP_ID)
  }

  private fun getNavigatables(shelvedChanges: MutableList<ShelvedWrapper>): MutableList<Navigatable?> {
    val navigatables = ArrayList<Navigatable?>()
    for (shelvedChange in shelvedChanges) {
      if (shelvedChange.getBeforePath() != null && FileStatus.ADDED != shelvedChange.getFileStatus()) {
        val navigatable: NavigatableAdapter = object : NavigatableAdapter() {
          override fun navigate(requestFocus: Boolean) {
            val vf = shelvedChange.getBeforeVFUnderProject(myProject)
            if (vf != null) {
              NavigatableAdapter.navigate(myProject, vf, true)
            }
          }
        }
        navigatables.add(navigatable)
      }
    }
    return navigatables
  }

  fun invalidateDataAndRefresh(onRefreshed: Runnable?) {
    myAsyncTreeModel.invalidateData()
    requestRefresh(onRefreshed)
  }
}
