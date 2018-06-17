// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import java.util.List;

/**
 * @deprecated Use {@link SimpleChangesBrowser}
 */
@Deprecated
public class ChangesBrowser extends OldChangesBrowserBase<Change> {

  public ChangesBrowser(@NotNull Project project,
                        @Nullable List<? extends ChangeList> changeLists,
                        @NotNull List<Change> changes,
                        @Nullable ChangeList initialListSelection,
                        boolean capableOfExcludingChanges,
                        boolean highlightProblems,
                        @Nullable Runnable inclusionListener,
                        @NotNull MyUseCase useCase,
                        @Nullable VirtualFile toSelect) {
    super(project, changes, capableOfExcludingChanges, highlightProblems, inclusionListener, useCase, toSelect, Change.class);

    init();
    setInitialSelection(changeLists, changes, initialListSelection);
    rebuildList();
  }

  @NotNull
  protected DefaultTreeModel buildTreeModel(final List<Change> changes, ChangeNodeDecorator changeNodeDecorator, boolean showFlatten) {
    return TreeModelBuilder.buildFromChanges(myProject, myViewer.getGrouping(), changes, changeNodeDecorator);
  }

  @NotNull
  protected List<Change> getSelectedObjects(@NotNull final ChangesBrowserNode<?> node) {
    return node.getAllChangesUnder();
  }

  @Nullable
  protected Change getLeadSelectedObject(@NotNull final ChangesBrowserNode<?> node) {
    final Object o = node.getUserObject();
    if (o instanceof Change) {
      return (Change)o;
    }
    return null;
  }

  @NotNull
  @Override
  public List<Change> getSelectedChanges() {
    return myViewer.getSelectedChanges();
  }

  @NotNull
  @Override
  public List<Change> getAllChanges() {
    return myViewer.getChanges();
  }

  @NotNull
  @Override
  public List<Change> getCurrentDisplayedChanges() {
    return myChangesToDisplay != null ? myChangesToDisplay : super.getCurrentDisplayedChanges();
  }

  @NotNull
  @Override
  public List<Change> getCurrentIncludedChanges() {
    return ContainerUtil.newArrayList(myViewer.getIncludedChanges());
  }

  @NotNull
  @Override
  public List<Change> getCurrentDisplayedObjects() {
    return getCurrentDisplayedChanges();
  }

  public enum MyUseCase {
    LOCAL_CHANGES,
    COMMITTED_CHANGES
  }
}
