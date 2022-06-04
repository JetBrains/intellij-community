// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.border.Border;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.*;

/**
 * @deprecated Use {@link ChangesTree} or {@link ChangesTreeImpl}
 */
@Deprecated
public abstract class ChangesTreeList<T> extends ChangesTree {
  @NotNull private final List<T> myRawChanges = new ArrayList<>();

  @Nullable private ChangeNodeDecorator myChangeDecorator;


  public ChangesTreeList(@NotNull Project project,
                         @NotNull Collection<T> initiallyIncluded,
                         boolean showCheckboxes,
                         boolean highlightProblems,
                         @Nullable Runnable inclusionListener,
                         @Nullable ChangeNodeDecorator decorator) {
    super(project, showCheckboxes, highlightProblems);
    setIncludedChanges(initiallyIncluded);
    setInclusionListener(inclusionListener);
    myChangeDecorator = decorator;
  }

  public ChangesTreeList(@NotNull Project project,
                         boolean showCheckboxes,
                         boolean highlightProblems) {
    super(project, showCheckboxes, highlightProblems);
  }

  public void setChangeDecorator(@Nullable ChangeNodeDecorator changeDecorator) {
    myChangeDecorator = changeDecorator;
    rebuildTree();
  }

  /**
   * Does nothing as ChangesTreeList is currently not wrapped in JScrollPane by default.
   * Left not to break API (used in several plugins).
   *
   * @deprecated to remove in 2017.
   */
  @SuppressWarnings("unused")
  @Deprecated(forRemoval = true)
  public void setScrollPaneBorder(Border border) {
  }

  public void setChangesToDisplay(final List<? extends T> changes) {
    setChangesToDisplay(changes, null);
  }

  public void setChangesToDisplay(final List<? extends T> changes, @Nullable final VirtualFile toSelect) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myProject.isDisposed()) return;

    myRawChanges.clear();
    myRawChanges.addAll(changes);

    rebuildTree();
    if (toSelect != null) selectFile(toSelect);
  }


  protected abstract DefaultTreeModel buildTreeModel(final List<T> changes, final ChangeNodeDecorator changeNodeDecorator);

  @Override
  public void rebuildTree() {
    DefaultTreeModel newModel = buildTreeModel(myRawChanges, myChangeDecorator);
    updateTreeModel(newModel);
  }

  @NotNull
  public List<T> getChanges() {
    return getSelectedObjects(getRoot());
  }

  @NotNull
  public List<T> getSelectedChanges() {
    final TreePath[] paths = getSelectionPaths();
    if (paths == null) {
      return Collections.emptyList();
    }
    else {
      LinkedHashSet<T> changes = new LinkedHashSet<>();
      for (TreePath path : paths) {
        changes.addAll(getSelectedObjects((ChangesBrowserNode)path.getLastPathComponent()));
      }
      return new ArrayList<>(changes);
    }
  }

  @NotNull
  private List<T> getSelectedChangesOrAllIfNone() {
    List<T> changes = getSelectedChanges();
    if (!changes.isEmpty()) return changes;
    return getChanges();
  }

  protected abstract List<T> getSelectedObjects(final ChangesBrowserNode<?> node);

  @Nullable
  protected abstract T getLeadSelectedObject(final ChangesBrowserNode<?> node);

  @Nullable
  public T getHighestLeadSelection() {
    final TreePath path = getSelectionPath();
    if (path == null) {
      return null;
    }

    return getLeadSelectedObject((ChangesBrowserNode)path.getLastPathComponent());
  }

  @Nullable
  public T getLeadSelection() {
    final TreePath path = getSelectionPath();

    return path == null ? null : ContainerUtil.getFirstItem(getSelectedObjects(((ChangesBrowserNode)path.getLastPathComponent())));
  }

  @NotNull
  public Collection<T> getIncludedChanges() {
    Set<Object> includedSet = getIncludedSet();
    return ContainerUtil.filter(getChanges(), includedSet::contains);
  }

  public void select(@NotNull Collection<T> changes) {
    setSelectedChanges(changes);
  }

  public void setAlwaysExpandList(boolean alwaysExpandList) {
    setKeepTreeState(!alwaysExpandList);
  }
}
