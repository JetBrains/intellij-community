// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf;

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.TwoStepAsyncChangesTreeModel
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.DefaultTreeModel

@ApiStatus.Internal
internal class ShelfTreeAsyncModel(val project: Project, scope: CoroutineScope)
  : TwoStepAsyncChangesTreeModel<MutableList<ShelvedChangeList>>(scope) {

  override fun fetchData(): MutableList<ShelvedChangeList> {
    val lists = ShelveChangesManager.getInstance(project).allLists
    lists.forEach{ it.loadChangesIfNeeded(project) }
    return ContainerUtil.sorted(lists, ChangelistComparator)
  }

  override fun buildTreeModelSync(
    changeLists: MutableList<ShelvedChangeList>,
    grouping: ChangesGroupingPolicyFactory,
  ): DefaultTreeModel {
    val showRecycled = ShelveChangesManager.getInstance(project).isShowRecycled
    val modelBuilder = ShelvedTreeModelBuilder(project, grouping)
    modelBuilder.setShelvedLists(changeLists.filter { !it.isDeleted && (showRecycled || !it.isRecycled) })
    modelBuilder.setDeletedShelvedLists(changeLists.filter { it.isDeleted })
    return modelBuilder.build()
  }
}
