// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultTreeModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class ChangesTreeImpl<T> extends ChangesTree {
  @NotNull private final List<T> myChanges = new ArrayList<>();
  @NotNull private final Class<T> myClazz;

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
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myProject.isDisposed()) return;

    myChanges.clear();
    myChanges.addAll(changes);

    rebuildTree();
  }


  @NotNull
  protected abstract DefaultTreeModel buildTreeModel(@NotNull List<T> changes);

  @Override
  public void rebuildTree() {
    DefaultTreeModel newModel = buildTreeModel(myChanges);
    updateTreeModel(newModel);
  }


  @NotNull
  public List<T> getChanges() {
    return VcsTreeModelData.all(this).userObjects(myClazz);
  }

  @NotNull
  public List<T> getSelectedChanges() {
    return VcsTreeModelData.selected(this).userObjects(myClazz);
  }

  @NotNull
  public Collection<T> getIncludedChanges() {
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
                   @NotNull List<Change> changes) {
      super(project, showCheckboxes, highlightProblems, Change.class, changes);
    }

    @NotNull
    @Override
    protected DefaultTreeModel buildTreeModel(@NotNull List<Change> changes) {
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
                     @NotNull List<FilePath> paths) {
      super(project, showCheckboxes, highlightProblems, FilePath.class, paths);
    }

    @NotNull
    @Override
    protected DefaultTreeModel buildTreeModel(@NotNull List<FilePath> changes) {
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
                        @NotNull List<VirtualFile> files) {
      super(project, showCheckboxes, highlightProblems, VirtualFile.class, files);
    }

    @NotNull
    @Override
    protected DefaultTreeModel buildTreeModel(@NotNull List<VirtualFile> changes) {
      return TreeModelBuilder.buildFromVirtualFiles(myProject, getGrouping(), changes);
    }
  }
}
