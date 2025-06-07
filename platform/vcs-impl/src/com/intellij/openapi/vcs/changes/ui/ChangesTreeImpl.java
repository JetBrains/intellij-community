// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultTreeModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @deprecated Prefer using {@link AsyncChangesTreeImpl} instead.
 */
@Deprecated
public abstract class ChangesTreeImpl<T> extends ChangesTree {
  private final @NotNull List<T> myChanges = new ArrayList<>();
  private final @NotNull Class<T> myClazz;

  public ChangesTreeImpl(@NotNull Project project,
                         boolean showCheckboxes,
                         boolean highlightProblems,
                         @NotNull Class<T> clazz) {
    super(project, showCheckboxes, highlightProblems);
    myClazz = clazz;
  }

  public ChangesTreeImpl(@NotNull Project project,
                         boolean showCheckboxes,
                         boolean highlightProblems,
                         @NotNull Class<T> clazz,
                         @NotNull List<? extends T> changes) {
    this(project, showCheckboxes, highlightProblems, clazz);
    if (showCheckboxes) setIncludedChanges(changes);
    setChangesToDisplay(changes);
  }

  public void setChangesToDisplay(@NotNull Collection<? extends T> changes) {
    ThreadingAssertions.assertEventDispatchThread();
    if (myProject.isDisposed()) return;

    myChanges.clear();
    myChanges.addAll(changes);

    rebuildTree();
  }


  protected abstract @NotNull DefaultTreeModel buildTreeModel(@NotNull List<? extends T> changes);

  @Override
  public void rebuildTree() {
    DefaultTreeModel newModel = buildTreeModel(myChanges);
    updateTreeModel(newModel);
  }


  public @NotNull List<T> getChanges() {
    return VcsTreeModelData.all(this).userObjects(myClazz);
  }

  public @NotNull List<T> getSelectedChanges() {
    return VcsTreeModelData.selected(this).userObjects(myClazz);
  }

  public @NotNull Collection<T> getIncludedChanges() {
    return VcsTreeModelData.included(this).userObjects(myClazz);
  }

  public static class Changes extends ChangesTreeImpl<Change> {
    public Changes(@NotNull Project project,
                   boolean showCheckboxes,
                   boolean highlightProblems) {
      super(project, showCheckboxes, highlightProblems, Change.class);
    }

    public Changes(@NotNull Project project,
                   boolean showCheckboxes,
                   boolean highlightProblems,
                   @NotNull List<? extends Change> changes) {
      super(project, showCheckboxes, highlightProblems, Change.class, changes);
    }

    @Override
    protected @NotNull DefaultTreeModel buildTreeModel(@NotNull List<? extends Change> changes) {
      return TreeModelBuilder.buildFromChanges(myProject, getGrouping(), changes, null);
    }
  }

  public static class FilePaths extends ChangesTreeImpl<FilePath> {
    public FilePaths(@NotNull Project project,
                     boolean showCheckboxes,
                     boolean highlightProblems) {
      super(project, showCheckboxes, highlightProblems, FilePath.class);
    }

    public FilePaths(@NotNull Project project,
                     boolean showCheckboxes,
                     boolean highlightProblems,
                     @NotNull List<? extends FilePath> paths) {
      super(project, showCheckboxes, highlightProblems, FilePath.class, paths);
    }

    @Override
    protected @NotNull DefaultTreeModel buildTreeModel(@NotNull List<? extends FilePath> changes) {
      return TreeModelBuilder.buildFromFilePaths(myProject, getGrouping(), changes);
    }
  }

  public static class VirtualFiles extends ChangesTreeImpl<VirtualFile> {
    public VirtualFiles(@NotNull Project project,
                        boolean showCheckboxes,
                        boolean highlightProblems) {
      super(project, showCheckboxes, highlightProblems, VirtualFile.class);
    }

    public VirtualFiles(@NotNull Project project,
                        boolean showCheckboxes,
                        boolean highlightProblems,
                        @NotNull List<? extends VirtualFile> files) {
      super(project, showCheckboxes, highlightProblems, VirtualFile.class, files);
    }

    @Override
    protected @NotNull DefaultTreeModel buildTreeModel(@NotNull List<? extends VirtualFile> changes) {
      return TreeModelBuilder.buildFromVirtualFiles(myProject, getGrouping(), changes);
    }
  }
}
