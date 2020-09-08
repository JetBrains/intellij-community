// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.dnd.DnDActionInfo;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.DefaultChangeListOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager.unshelveSilentlyWithDnd;
import static com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.IGNORED_FILES_TAG;
import static com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.UNVERSIONED_FILES_TAG;
import static com.intellij.openapi.vcs.changes.ui.ChangesListView.getChanges;
import static com.intellij.openapi.vcs.changes.ui.ChangesListView.getFilePaths;
import static java.util.stream.Collectors.toList;

public final class ChangesViewDnDSupport extends ChangesTreeDnDSupport {
  @NotNull private final Project myProject;

  public static void install(@NotNull Project project, @NotNull ChangesTree tree, @NotNull Disposable disposable) {
    new ChangesViewDnDSupport(project, tree).install(disposable);
  }

  private ChangesViewDnDSupport(@NotNull Project project, @NotNull ChangesTree tree) {
    super(tree);
    myProject = project;
  }

  @Nullable
  @Override
  protected DnDDragStartBean createDragStartBean(@NotNull DnDActionInfo info) {
    if (info.isMove()) {
      Change[] changes = getChanges(myProject, myTree.getSelectionPaths()).toArray(Change[]::new);
      List<FilePath> unversionedFiles = getFilePaths(myTree.getSelectionPaths(), UNVERSIONED_FILES_TAG).collect(toList());
      List<FilePath> ignoredFiles = getFilePaths(myTree.getSelectionPaths(), IGNORED_FILES_TAG).collect(toList());

      if (changes.length > 0 || !unversionedFiles.isEmpty() || !ignoredFiles.isEmpty()) {
        return new DnDDragStartBean(new ChangeListDragBean(myTree, changes, unversionedFiles, ignoredFiles));
      }
    }

    return null;
  }

  @Override
  protected boolean canHandleDropEvent(@NotNull DnDEvent aEvent, @NotNull ChangesBrowserNode<?> dropNode) {
    Object attached = aEvent.getAttachedObject();
    if (attached instanceof ChangeListDragBean) {
      final ChangeListDragBean dragBean = (ChangeListDragBean)attached;
      dragBean.setTargetNode(dropNode);
      return dragBean.getSourceComponent() == myTree && dropNode.canAcceptDrop(dragBean);
    }
    else if (attached instanceof ShelvedChangeListDragBean) {
      return dropNode instanceof ChangesBrowserChangeListNode;
    }
    return false;
  }

  @Override
  public void drop(DnDEvent aEvent) {
    Object attached = aEvent.getAttachedObject();
    if (attached instanceof ShelvedChangeListDragBean) {
      unshelveSilentlyWithDnd(myProject, (ShelvedChangeListDragBean)attached, getDropRootNode(myTree, aEvent), !isCopyAction(aEvent));
    }
    else if (attached instanceof ChangeListDragBean) {
      final ChangeListDragBean dragBean = (ChangeListDragBean)attached;
      final ChangesBrowserNode<?> changesBrowserNode = dragBean.getTargetNode();
      if (changesBrowserNode != null) {
        changesBrowserNode.acceptDrop(new DefaultChangeListOwner(myProject), dragBean);
      }
    }
  }
}