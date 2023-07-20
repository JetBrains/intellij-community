// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Call {@link #shutdown()} when the browser is no longer needed.
 */
public class SimpleAsyncChangesBrowser extends AsyncChangesBrowserBase {
  private @NotNull List<Change> myChanges = new ArrayList<>();
  private @Nullable ChangeNodeDecorator myChangeNodeDecorator;

  public SimpleAsyncChangesBrowser(@NotNull Project project,
                                   boolean showCheckboxes,
                                   boolean highlightProblems) {
    super(project, showCheckboxes, highlightProblems);
    init();
  }


  @NotNull
  @Override
  protected final AsyncChangesTreeModel getChangesTreeModel() {
    return SimpleAsyncChangesTreeModel.create(grouping -> {
      return TreeModelBuilder.buildFromChanges(myProject, grouping, myChanges, myChangeNodeDecorator);
    });
  }

  public void setChangesToDisplay(@NotNull Collection<? extends Change> changes) {
    myChanges = new ArrayList<>(changes);
    myViewer.rebuildTree();
  }

  public void setChangesToDisplay(@NotNull Collection<? extends Change> changes,
                                  @NotNull ChangesTree.TreeStateStrategy<?> treeStateStrategy) {
    myChanges = new ArrayList<>(changes);
    myViewer.rebuildTree(treeStateStrategy);
  }

  public void setChangeNodeDecorator(@Nullable ChangeNodeDecorator value) {
    myChangeNodeDecorator = value;
    myViewer.rebuildTree();
  }

  public void setIncludedChanges(@NotNull Collection<? extends Change> changes) {
    myViewer.setIncludedChanges(changes);
  }


  @NotNull
  public List<Change> getAllChanges() {
    return VcsTreeModelData.all(myViewer).userObjects(Change.class);
  }

  @NotNull
  public List<Change> getSelectedChanges() {
    return VcsTreeModelData.selected(myViewer).userObjects(Change.class);
  }

  @NotNull
  public List<Change> getIncludedChanges() {
    return VcsTreeModelData.included(myViewer).userObjects(Change.class);
  }
}