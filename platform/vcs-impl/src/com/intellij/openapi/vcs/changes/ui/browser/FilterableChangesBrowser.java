// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui.browser;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.progress.util.ProgressIndicatorWithDelayedPresentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.ui.components.ProgressBarLoadingDecorator;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

@ApiStatus.Internal
public abstract class FilterableChangesBrowser extends ChangesBrowserBase implements Disposable {
  private final ChangesFilterer myChangesFilterer;
  private ProgressBarLoadingDecorator myLoadingDecorator;

  protected FilterableChangesBrowser(@NotNull Project project,
                                     boolean showCheckboxes,
                                     boolean highlightProblems) {
    super(project, showCheckboxes, highlightProblems);

    myChangesFilterer = new ChangesFilterer(myProject, this::updateTreeOnFilterChange);
    Disposer.register(this, myChangesFilterer);
  }

  private void updateTreeOnFilterChange() {
    myViewer.rebuildTree(ChangesTree.ALWAYS_KEEP);
    myViewer.expandDefaults();

    float progress = myChangesFilterer.getProgress();
    if (progress == 1.0f) {
      myLoadingDecorator.stopLoading();
    }
    else {
      myLoadingDecorator.startLoading();
      myLoadingDecorator.getProgressBar().setIndeterminate(progress == 0.0f);
      myLoadingDecorator.getProgressBar().setValue((int)(100 * progress));
    }

    onActiveChangesFilterChanges();
  }

  protected void onActiveChangesFilterChanges() { }

  @Override
  public void dispose() {
  }

  public boolean hasActiveChangesFilter() {
    return myChangesFilterer.hasActiveFilter();
  }

  public void clearActiveChangesFilter() {
    myChangesFilterer.clearFilter();
  }

  @RequiresEdt
  public ChangesFilterer.FilteredState filterChanges(@NotNull List<? extends Change> changes, boolean shouldFilter) {
    if (!shouldFilter) {
      myChangesFilterer.setChanges(null);
      return ChangesFilterer.FilteredState.create(changes);
    }
    else {
      myChangesFilterer.setChanges(changes);
      return myChangesFilterer.getFilteredChanges();
    }
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    sink.set(ChangesFilterer.DATA_KEY, myChangesFilterer);
  }

  @Override
  protected @NotNull JComponent createCenterPanel() {
    JComponent centerPanel = super.createCenterPanel();
    myLoadingDecorator = new ProgressBarLoadingDecorator(JBUI.Panels.simplePanel(centerPanel), this,
                                                         ProgressIndicatorWithDelayedPresentation.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS);
    return myLoadingDecorator.getComponent();
  }

  protected static void setFilteredChanges(@NotNull TreeModelBuilder builder,
                                           @NotNull ChangesFilterer.FilteredState filteredState,
                                           @Nullable ChangeNodeDecorator changeNodeDecorator) {
    builder.setChanges(filteredState.getChanges(), changeNodeDecorator);
    setPendingChanges(builder, filteredState.getPending(), changeNodeDecorator);
    setFilteredOutChanges(builder, filteredState.getFilteredOut(), changeNodeDecorator);
  }

  protected static void setPendingChanges(@NotNull TreeModelBuilder builder,
                                          @NotNull Collection<? extends Change> changes,
                                          @Nullable ChangeNodeDecorator changeNodeDecorator) {
    if (changes.isEmpty()) return;

    ChangesBrowserNode<?> tagNode = builder.createTagNode(VcsBundle.message("changes.nodetitle.filter.pending"), false);
    builder.insertChanges(changes, tagNode, changeNodeDecorator);
  }

  protected static void setFilteredOutChanges(@NotNull TreeModelBuilder builder,
                                              @NotNull Collection<? extends Change> changes,
                                              @Nullable ChangeNodeDecorator changeNodeDecorator) {
    if (changes.isEmpty()) return;

    ChangesBrowserNode<?> tagNode = builder.createTagNode(VcsBundle.message("changes.nodetitle.filtered.out"), false);
    builder.insertChanges(changes, tagNode, changeNodeDecorator);
  }
}
