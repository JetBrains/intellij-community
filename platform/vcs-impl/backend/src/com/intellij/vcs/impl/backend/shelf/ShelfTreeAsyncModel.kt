// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager;
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory;
import com.intellij.openapi.vcs.changes.ui.TwoStepAsyncChangesTreeModel;
import com.intellij.util.containers.ContainerUtil;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultTreeModel;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.filter;
import static com.intellij.util.containers.ContainerUtil.sorted;

class ShelfTreeAsyncModel extends TwoStepAsyncChangesTreeModel<List<ShelvedChangeList>> {
  private final Project myProject;

  @ApiStatus.Internal
  ShelfTreeAsyncModel(@NotNull Project project, @NotNull CoroutineScope scope) {
    super(scope);
    myProject = project;
  }

  @Override
  public List<ShelvedChangeList> fetchData() {
    List<ShelvedChangeList> lists = ShelveChangesManager.getInstance(myProject).getAllLists();
    lists.forEach(l -> l.loadChangesIfNeeded(myProject));
    return ContainerUtil.sorted(lists, ShelvedChangesViewManager.ChangelistComparator.getInstance());
  }

  @Override
  public @NotNull DefaultTreeModel buildTreeModelSync(@NotNull List<ShelvedChangeList> changeLists,
                                                      @NotNull ChangesGroupingPolicyFactory grouping) {
    boolean showRecycled = ShelveChangesManager.getInstance(myProject).isShowRecycled();
    ShelvedTreeModelBuilder modelBuilder = new ShelvedTreeModelBuilder(myProject, grouping);
    modelBuilder.setShelvedLists(filter(changeLists, l -> !l.isDeleted() && (showRecycled || !l.isRecycled())));
    modelBuilder.setDeletedShelvedLists(filter(changeLists, ShelvedChangeList::isDeleted));
    return modelBuilder.build();
  }
}
