// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.browser.ChangesFilterer;
import com.intellij.openapi.vcs.changes.ui.browser.FilterableChangesBrowser;
import com.intellij.vcs.log.VcsLogBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SimpleChangesBrowser extends FilterableChangesBrowser {
  private final List<Change> myChanges = new ArrayList<>();
  @Nullable private ChangeNodeDecorator myChangeNodeDecorator;

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


  @NotNull
  @Override
  protected DefaultTreeModel buildTreeModel() {
    ChangesFilterer.FilteredState filteredChanges = filterChanges(myChanges, true);

    TreeModelBuilder builder = new TreeModelBuilder(myProject, getGrouping());
    setFilteredChanges(builder, filteredChanges, myChangeNodeDecorator);
    return builder.build();
  }

  @Override
  protected @NotNull List<AnAction> createToolbarActions() {
    List<AnAction> actions = new ArrayList<>(super.createToolbarActions());
    actions.add(ActionManager.getInstance().getAction("ChangesBrowser.FiltererGroup"));
    return actions;
  }

  public void setChangesToDisplay(@NotNull Collection<? extends Change> changes) {
    myChanges.clear();
    myChanges.addAll(changes);

    if (changes.isEmpty()) {
      myViewer.setEmptyText(DiffBundle.message("diff.count.differences.status.text", 0));
    }
    else {
      myViewer.setEmptyText(VcsLogBundle.message("vcs.log.changes.no.changes.that.affect.selected.filters.status"));
    }

    myViewer.rebuildTree();
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