// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @deprecated Prefer using {@link SimpleAsyncChangesBrowser} instead.
 */
@Deprecated
public class SimpleChangesBrowser extends ChangesBrowserBase {
  private final List<Change> myChanges = new ArrayList<>();
  private @Nullable ChangeNodeDecorator myChangeNodeDecorator;

  public SimpleChangesBrowser(@NotNull Project project,
                              @NotNull Collection<? extends Change> changes) {
    this(project, false, false);
    setChangesToDisplay(changes);
  }

  public SimpleChangesBrowser(@NotNull Project project,
                              boolean showCheckboxes,
                              boolean highlightProblems) {
    super(project, showCheckboxes, highlightProblems);
    init();
  }


  /**
   * @deprecated Consider overriding {@link ChangesBrowserBase} instead.
   */
  @Override
  @Deprecated
  protected @NotNull DefaultTreeModel buildTreeModel() {
    return TreeModelBuilder.buildFromChanges(myProject, getGrouping(), myChanges, myChangeNodeDecorator);
  }


  public void setChangesToDisplay(@NotNull Collection<? extends Change> changes) {
    myChanges.clear();
    myChanges.addAll(changes);
    myViewer.rebuildTree();
  }

  public void setChangeNodeDecorator(@Nullable ChangeNodeDecorator value) {
    myChangeNodeDecorator = value;
    myViewer.rebuildTree();
  }

  public void setIncludedChanges(@NotNull Collection<? extends Change> changes) {
    myViewer.setIncludedChanges(changes);
  }


  public @NotNull List<Change> getAllChanges() {
    return VcsTreeModelData.all(myViewer).userObjects(Change.class);
  }

  public @NotNull List<Change> getSelectedChanges() {
    return VcsTreeModelData.selected(myViewer).userObjects(Change.class);
  }

  public @NotNull List<Change> getIncludedChanges() {
    return VcsTreeModelData.included(myViewer).userObjects(Change.class);
  }
}