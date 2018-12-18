// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeList
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.RemoteRevisionsCache
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.tree.DefaultTreeModel

class AlienChangeListBrowser(project: Project,
                             private val myChangeList: LocalChangeList,
                             private val myChanges: List<Change>) : CommitDialogChangesBrowser(project, false, true) {
  init {
    init()
  }

  override fun buildTreeModel(): DefaultTreeModel {
    val decorator = RemoteRevisionsCache.getInstance(myProject).changesNodeDecorator
    return TreeModelBuilder.buildFromChanges(myProject, grouping, myChanges, decorator)
  }

  override fun getSelectedChangeList(): LocalChangeList = myChangeList

  override fun getDisplayedChanges(): List<Change> = myChanges
  override fun getSelectedChanges(): List<Change> = VcsTreeModelData.selected(myViewer).userObjects(Change::class.java)
  override fun getIncludedChanges(): List<Change> = myChanges

  override fun getDisplayedUnversionedFiles(): List<VirtualFile> = emptyList()
  override fun getSelectedUnversionedFiles(): List<VirtualFile> = emptyList()
  override fun getIncludedUnversionedFiles(): List<VirtualFile> = emptyList()

  override fun updateDisplayedChangeLists() {}

  override fun getData(dataId: String) = when (dataId) {
    VcsDataKeys.CHANGE_LISTS.name -> arrayOf<ChangeList>(myChangeList)
    else -> super.getData(dataId)
  }
}
