// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.dnd.DnDActionInfo;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager.unshelveSilentlyWithDnd;
import static com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.IGNORED_FILES_TAG;
import static com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.UNVERSIONED_FILES_TAG;

@ApiStatus.Internal
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
      List<Change> changes = VcsTreeModelData.selected(myTree)
        .iterateUserObjects(Change.class).toList();
      List<FilePath> unversionedFiles = VcsTreeModelData.selectedUnderTag(myTree, UNVERSIONED_FILES_TAG)
        .iterateUserObjects(FilePath.class).toList();
      List<FilePath> ignoredFiles = VcsTreeModelData.selectedUnderTag(myTree, IGNORED_FILES_TAG)
        .iterateUserObjects(FilePath.class).toList();

      if (!changes.isEmpty() || !unversionedFiles.isEmpty() || !ignoredFiles.isEmpty()) {
        return new DnDDragStartBean(new ChangeListDragBean(myTree, changes, unversionedFiles, ignoredFiles));
      }
    }

    return null;
  }

  @Override
  protected boolean canHandleDropEvent(@NotNull DnDEvent aEvent, @Nullable ChangesBrowserNode<?> dropNode) {
    Object attached = aEvent.getAttachedObject();
    if (attached instanceof ChangeListDragBean) {
      if (dropNode != null) {
        final ChangeListDragBean dragBean = (ChangeListDragBean)attached;
        dragBean.setTargetNode(dropNode);
        return dragBean.getSourceComponent() == myTree && dropNode.canAcceptDrop(dragBean);
      }
    }
    else if (attached instanceof ShelvedChangeListDragBean) {
      return dropNode == null ||
             dropNode instanceof ChangesBrowserChangeListNode;
    }
    return false;
  }

  @Override
  public void drop(DnDEvent aEvent) {
    Object attached = aEvent.getAttachedObject();
    if (attached instanceof ShelvedChangeListDragBean dragBean) {
      ChangesBrowserNode<?> dropRootNode = getDropRootNode(myTree, aEvent);
      LocalChangeList targetChangeList;
      if (dropRootNode != null) {
        targetChangeList = TreeUtil.getUserObject(LocalChangeList.class, dropRootNode);
      }
      else {
        ShelvedChangeList changeList = ContainerUtil.getFirstItem(dragBean.getShelvedChangelists());
        String suggestedName = changeList != null ? changeList.getName() : VcsBundle.message("changes.new.changelist");
        String listName = ChangeListUtil.createNameForChangeList(myProject, suggestedName);
        targetChangeList = ChangeListManager.getInstance(myProject).addChangeList(listName, null);
      }
      unshelveSilentlyWithDnd(myProject, dragBean, targetChangeList, !isCopyAction(aEvent));
    }
    else if (attached instanceof ChangeListDragBean dragBean) {
      final ChangesBrowserNode<?> changesBrowserNode = dragBean.getTargetNode();
      if (changesBrowserNode != null) {
        changesBrowserNode.acceptDrop(new DefaultChangeListOwner(myProject), dragBean);
      }
    }
  }
}