// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NavigatableAdapter;
import com.intellij.ui.TreeSpeedSearch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.map2Array;

final class ShelfTree extends AsyncChangesTree {
  private final DeleteProvider myDeleteProvider = new ShelvedChangesViewManager.MyShelveDeleteProvider(myProject, this);
  private final ShelfTreeAsyncModel myAsyncTreeModel;

  private ShelfTree(@NotNull Project project) {
    super(project, false, false, false);
    myAsyncTreeModel = new ShelfTreeAsyncModel(project, getScope());

    TreeSpeedSearch.installOn(this, true, ChangesBrowserNode.TO_TEXT_CONVERTER);
    setKeepTreeState(true);
  }

  @Override
  protected @NotNull AsyncChangesTreeModel getChangesTreeModel() {
    return myAsyncTreeModel;
  }

  @Override
  public boolean isPathEditable(TreePath path) {
    return isEditable() && getSelectionCount() == 1 && path.getLastPathComponent() instanceof ShelvedListNode;
  }

  @Override
  protected @NotNull ChangesGroupingSupport installGroupingSupport() {
    ChangesGroupingSupport groupingSupport = new ChangesGroupingSupport(myProject, this, false);
    ChangesTree.installGroupingSupport(this, groupingSupport,
                                       () -> ShelveChangesManager.getInstance(myProject).getGrouping(),
                                       (newGrouping) -> ShelveChangesManager.getInstance(myProject).setGrouping(newGrouping));
    return groupingSupport;
  }

  @Override
  public int getToggleClickCount() {
    return 2;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    sink.set(ShelvedChangesViewManager.SHELVED_CHANGES_TREE, this);
    sink.set(ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY, new ArrayList<>(
      ShelvedChangesViewManager.getSelectedLists(this, l -> !l.isRecycled() && !l.isDeleted())));
    sink.set(ShelvedChangesViewManager.SHELVED_RECYCLED_CHANGELIST_KEY, new ArrayList<>(
      ShelvedChangesViewManager.getSelectedLists(this, l -> l.isRecycled() && !l.isDeleted())));
    sink.set(ShelvedChangesViewManager.SHELVED_DELETED_CHANGELIST_KEY,
             new ArrayList<>(ShelvedChangesViewManager.getSelectedLists(this, l -> l.isDeleted())));
    sink.set(ShelvedChangesViewManager.SHELVED_CHANGE_KEY, VcsTreeModelData.selected(this).iterateUserObjects(ShelvedWrapper.class)
      .filterMap(s -> s.getShelvedChange())
      .toList());
    sink.set(ShelvedChangesViewManager.SHELVED_BINARY_FILE_KEY, VcsTreeModelData.selected(this).iterateUserObjects(ShelvedWrapper.class)
      .filterMap(s -> s.getBinaryFile())
      .toList());
    if (!isEditing()) {
      sink.set(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, myDeleteProvider);
    }
    List<ShelvedWrapper> shelvedChanges = VcsTreeModelData.selected(this).userObjects(ShelvedWrapper.class);
    if (!shelvedChanges.isEmpty()) {
      sink.set(VcsDataKeys.CHANGES, map2Array(shelvedChanges, Change.class, s -> s.getChangeWithLocal(myProject)));
    }
    sink.set(CommonDataKeys.NAVIGATABLE_ARRAY, getNavigatables(shelvedChanges)
      .toArray(Navigatable.EMPTY_NAVIGATABLE_ARRAY));
    sink.set(PlatformCoreDataKeys.HELP_ID, ShelvedChangesViewManager.HELP_ID);
  }

  private @NotNull List<Navigatable> getNavigatables(@NotNull List<ShelvedWrapper> shelvedChanges) {
    ArrayList<Navigatable> navigatables = new ArrayList<>();
    for (ShelvedWrapper shelvedChange : shelvedChanges) {
      if (shelvedChange.getBeforePath() != null && !FileStatus.ADDED.equals(shelvedChange.getFileStatus())) {
        NavigatableAdapter navigatable = new NavigatableAdapter() {
          @Override
          public void navigate(boolean requestFocus) {
            VirtualFile vf = shelvedChange.getBeforeVFUnderProject(myProject);
            if (vf != null) {
              navigate(myProject, vf, true);
            }
          }
        };
        navigatables.add(navigatable);
      }
    }
    return navigatables;
  }

  public void invalidateDataAndRefresh(@Nullable Runnable onRefreshed) {
    myAsyncTreeModel.invalidateData();
    requestRefresh(onRefreshed);
  }
}
