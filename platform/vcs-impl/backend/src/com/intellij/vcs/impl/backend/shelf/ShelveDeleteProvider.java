// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf;

import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFile;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChange;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class ShelveDeleteProvider implements DeleteProvider {
  private final @NotNull Project myProject;
  private final @NotNull ShelfTree myTree;

  private ShelveDeleteProvider(@NotNull Project project, @NotNull ShelfTree tree) {
    myProject = project;
    myTree = tree;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void deleteElement(@NotNull DataContext dataContext) {
    List<ShelvedChangeList> shelvedListsToDelete = TreeUtil.collectSelectedObjectsOfType(myTree, ShelvedChangeList.class);
    List<ShelvedChangeList> shelvedListsFromChanges = ShelvedChangesViewManager.getShelvedLists(dataContext);
    List<ShelvedChange> selectedChanges = ShelvedChangesViewManager.getShelveChanges(dataContext);
    List<ShelvedBinaryFile> selectedBinaryChanges = ShelvedChangesViewManager.getBinaryShelveChanges(dataContext);

    ShelvedChangesViewManager.deleteShelves(myProject, shelvedListsToDelete, shelvedListsFromChanges, selectedChanges,
                                            selectedBinaryChanges);
  }

  @Override
  public boolean canDeleteElement(@NotNull DataContext dataContext) {
    return !ShelvedChangesViewManager.getShelvedLists(dataContext).isEmpty();
  }
}
