// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultTreeModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class AsyncChangesTreeImpl<T> extends AsyncChangesTree {
  private @NotNull List<T> myChanges = Collections.emptyList();
  private final @NotNull Class<T> myClazz;

  public AsyncChangesTreeImpl(@NotNull Project project,
                              boolean showCheckboxes,
                              boolean highlightProblems,
                              @NotNull Class<T> clazz) {
    super(project, showCheckboxes, highlightProblems);
    myClazz = clazz;
  }

  public AsyncChangesTreeImpl(@NotNull Project project,
                              boolean showCheckboxes,
                              boolean highlightProblems,
                              @NotNull Class<T> clazz,
                              @NotNull List<? extends T> changes) {
    this(project, showCheckboxes, highlightProblems, clazz);
    if (showCheckboxes) setIncludedChanges(changes);
    setChangesToDisplay(changes);
  }

  public void setChangesToDisplay(@NotNull Collection<? extends T> changes) {
    if (myProject.isDisposed()) return;

    myChanges = new ArrayList<>(changes);

    rebuildTree();
  }

  @Override
  protected @NotNull AsyncChangesTreeModel getChangesTreeModel() {
    return SimpleAsyncChangesTreeModel.create(grouping -> buildTreeModel(grouping, myChanges));
  }

  @RequiresBackgroundThread
  protected abstract @NotNull DefaultTreeModel buildTreeModel(@NotNull ChangesGroupingPolicyFactory grouping, @NotNull List<? extends T> changes);

  public @NotNull List<T> getChanges() {
    return ContainerUtil.filterIsInstance(myChanges, myClazz);
  }

  public @NotNull List<T> getDisplayedChanges() {
    return VcsTreeModelData.all(this).userObjects(myClazz);
  }

  public @NotNull List<T> getSelectedChanges() {
    return VcsTreeModelData.selected(this).userObjects(myClazz);
  }

  public @NotNull Collection<T> getIncludedChanges() {
    return VcsTreeModelData.included(this).userObjects(myClazz);
  }


  public static class Changes extends AsyncChangesTreeImpl<Change> {
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
    protected @NotNull DefaultTreeModel buildTreeModel(@NotNull ChangesGroupingPolicyFactory grouping,
                                                       @NotNull List<? extends Change> changes) {
      return TreeModelBuilder.buildFromChanges(myProject, grouping, changes, null);
    }
  }

  public static class FilePaths extends AsyncChangesTreeImpl<FilePath> {
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
    protected @NotNull DefaultTreeModel buildTreeModel(@NotNull ChangesGroupingPolicyFactory grouping,
                                                       @NotNull List<? extends FilePath> changes) {
      return TreeModelBuilder.buildFromFilePaths(myProject, grouping, changes);
    }
  }

  public static class VirtualFiles extends AsyncChangesTreeImpl<VirtualFile> {
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
    protected @NotNull DefaultTreeModel buildTreeModel(@NotNull ChangesGroupingPolicyFactory grouping,
                                                       @NotNull List<? extends VirtualFile> changes) {
      return TreeModelBuilder.buildFromVirtualFiles(myProject, grouping, changes);
    }
  }
}
