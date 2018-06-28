// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.RemoteRevisionsCache;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import java.util.Collections;
import java.util.List;

public class AlienChangeListBrowser extends CommitDialogChangesBrowser {
  @NotNull private final LocalChangeList myChangeList;
  @NotNull private final List<Change> myChanges;

  public AlienChangeListBrowser(@NotNull Project project,
                                @NotNull LocalChangeList changelist,
                                @NotNull List<Change> changes) {
    super(project, false, true);
    myChangeList = changelist;
    myChanges = changes;

    init();
  }

  @NotNull
  @Override
  protected DefaultTreeModel buildTreeModel() {
    RemoteStatusChangeNodeDecorator decorator = RemoteRevisionsCache.getInstance(myProject).getChangesNodeDecorator();
    return TreeModelBuilder.buildFromChanges(myProject, getGrouping(), myChanges, decorator);
  }


  @NotNull
  @Override
  public LocalChangeList getSelectedChangeList() {
    return myChangeList;
  }


  @NotNull
  @Override
  public List<Change> getDisplayedChanges() {
    return myChanges;
  }

  @NotNull
  @Override
  public List<Change> getSelectedChanges() {
    return VcsTreeModelData.selected(myViewer).userObjects(Change.class);
  }

  @NotNull
  @Override
  public List<Change> getIncludedChanges() {
    return myChanges;
  }

  @NotNull
  @Override
  public List<VirtualFile> getDisplayedUnversionedFiles() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<VirtualFile> getSelectedUnversionedFiles() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<VirtualFile> getIncludedUnversionedFiles() {
    return Collections.emptyList();
  }

  @Override
  public void updateDisplayedChangeLists() {
  }


  @Nullable
  @Override
  public Object getData(String dataId) {
    if (VcsDataKeys.CHANGE_LISTS.is(dataId)) {
      return new ChangeList[]{myChangeList};
    }
    return super.getData(dataId);
  }
}
