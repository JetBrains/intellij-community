// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf;

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vcs.changes.EditorTabDiffPreviewManager.Companion.getInstance
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.TwoStepAsyncChangesTreeModel
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer
import javax.swing.tree.DefaultTreeModel

internal class ShelfTreeAsyncModel @ApiStatus.Internal constructor(
  project: Project,
  scope: CoroutineScope
) : TwoStepAsyncChangesTreeModel<MutableList<ShelvedChangeList?>?>(
  scope) {
  private val myProject: Project

  init {
    myProject = project
  }

  public override fun fetchData(): MutableList<ShelvedChangeList?> {
    val lists = ShelveChangesManager.getInstance(myProject).getAllLists()
    lists.forEach(Consumer { l: ShelvedChangeList? -> l!!.loadChangesIfNeeded(myProject) })
    return ContainerUtil.sorted(lists, ShelvedChangesViewManager.ChangelistComparator.getInstance())
  }

  public override fun buildTreeModelSync(
    changeLists: MutableList<ShelvedChangeList?>,
    grouping: ChangesGroupingPolicyFactory
  ): DefaultTreeModel {
    val showRecycled = ShelveChangesManager.getInstance(myProject).isShowRecycled()
    val modelBuilder = ShelvedTreeModelBuilder(myProject, grouping)
    modelBuilder.setShelvedLists(ContainerUtil.filter<ShelvedChangeList?>(changeLists,
                                                                          Condition { l: ShelvedChangeList? -> !l!!.isDeleted() && (showRecycled || !l.isRecycled()) }))
    modelBuilder.setDeletedShelvedLists(
      ContainerUtil.filter<ShelvedChangeList?>(changeLists, Condition { obj: ShelvedChangeList? -> obj!!.isDeleted() }))
    return modelBuilder.build()
  }
}
