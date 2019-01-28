/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.dnd.*;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.dnd.DnDConstants;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.vcs.changes.ChangesViewManager.getDropRootNode;
import static com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager.unshelveSilentlyWithDnd;
import static com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.IGNORED_FILES_TAG;
import static com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.UNVERSIONED_FILES_TAG;
import static com.intellij.openapi.vcs.changes.ui.ChangesListView.getChanges;
import static com.intellij.openapi.vcs.changes.ui.ChangesListView.getVirtualFiles;
import static java.util.stream.Collectors.toList;

public class ChangesDnDSupport implements DnDDropHandler, DnDTargetChecker {

  @NotNull private final Project myProject;
  @NotNull private final ChangeListManagerImpl myChangeListManager;
  @NotNull private final Tree myTree;

  public static void install(@NotNull Project project, @NotNull Tree tree) {
    new ChangesDnDSupport(project, tree).install();
  }

  private ChangesDnDSupport(@NotNull Project project, @NotNull Tree tree) {
    myProject = project;
    myChangeListManager = ChangeListManagerImpl.getInstanceImpl(project);
    myTree = tree;
  }

  private void install() {
    DnDSupport.createBuilder(myTree)
      .setTargetChecker(this)
      .setDropHandler(this)
      .setImageProvider(this::createDraggedImage)
      .setBeanProvider(this::createDragStartBean)
      .setDisposableParent(myTree instanceof Disposable ? (Disposable)myTree : myProject)
      .install();
  }

  @NotNull
  private DnDImage createDraggedImage(@NotNull DnDActionInfo info) {
    String imageText = VcsBundle.message("changes.view.dnd.label", getSelectionCount());
    Image image = DnDAwareTree.getDragImage(myTree, imageText, null).getFirst();

    return new DnDImage(image, new Point(-image.getWidth(null), -image.getHeight(null)));
  }

  @Nullable
  private DnDDragStartBean createDragStartBean(@NotNull DnDActionInfo info) {
    DnDDragStartBean result = null;

    if (info.isMove()) {
      Change[] changes = getChanges(myProject, myTree.getSelectionPaths()).toArray(Change[]::new);
      List<VirtualFile> unversionedFiles = getVirtualFiles(myTree.getSelectionPaths(), UNVERSIONED_FILES_TAG).collect(toList());
      List<VirtualFile> ignoredFiles = getVirtualFiles(myTree.getSelectionPaths(), IGNORED_FILES_TAG).collect(toList());

      if (changes.length > 0 || !unversionedFiles.isEmpty() || !ignoredFiles.isEmpty()) {
        result = new DnDDragStartBean(new ChangeListDragBean(myTree, changes, unversionedFiles, ignoredFiles));
      }
    }

    return result;
  }

  @Override
  public boolean update(DnDEvent aEvent) {
    aEvent.hideHighlighter();
    aEvent.setDropPossible(false, "");

    Object attached = aEvent.getAttachedObject();
    ChangesBrowserNode dropNode = getDropRootNode(myTree, aEvent);

    if (attached instanceof ChangeListDragBean) {
      final ChangeListDragBean dragBean = (ChangeListDragBean)attached;
      dragBean.setTargetNode(dropNode);
      if (dragBean.getSourceComponent() != myTree || dropNode == null || !dropNode.canAcceptDrop(dragBean)) return true;
    }
    else if (attached instanceof ShelvedChangeListDragBean) {
      if (!(dropNode == null || dropNode instanceof ChangesBrowserChangeListNode)) return true;
    }
    else {
      return true;
    }

    if (dropNode != null) {
      highlightDropNode(aEvent, dropNode);
    }

    aEvent.setDropPossible(true);
    return false;
  }

  private void highlightDropNode(@NotNull DnDEvent aEvent, @NotNull ChangesBrowserNode dropNode) {
    final Rectangle tableCellRect = myTree.getPathBounds(new TreePath(dropNode.getPath()));
    if (fitsInBounds(tableCellRect)) {
      aEvent.setHighlighting(new RelativeRectangle(myTree, tableCellRect), DnDEvent.DropTargetHighlightingType.RECTANGLE);
    }
  }

  @Override
  public void drop(DnDEvent aEvent) {
    Object attached = aEvent.getAttachedObject();
    if (attached instanceof ShelvedChangeListDragBean) {
      unshelveSilentlyWithDnd(myProject, (ShelvedChangeListDragBean)attached, getDropRootNode(myTree, aEvent), !isCopyAction(aEvent));
    }
    else if (attached instanceof ChangeListDragBean) {
      final ChangeListDragBean dragBean = (ChangeListDragBean)attached;
      final ChangesBrowserNode changesBrowserNode = dragBean.getTargetNode();
      if (changesBrowserNode != null) {
        changesBrowserNode.acceptDrop(myChangeListManager, dragBean);
      }
    }
  }

  public static boolean isCopyAction(@NotNull DnDEvent aEvent) {
    DnDAction eventAction = aEvent.getAction();
    return eventAction != null && eventAction.getActionId() == DnDConstants.ACTION_COPY;
  }


  private boolean fitsInBounds(final Rectangle rect) {
    final Container container = myTree.getParent();
    if (container instanceof JViewport) {
      final Container scrollPane = container.getParent();
      if (scrollPane instanceof JScrollPane) {
        final Rectangle rectangle = SwingUtilities.convertRectangle(myTree, rect, scrollPane.getParent());
        return scrollPane.getBounds().contains(rectangle);
      }
    }
    return true;
  }

  private int getSelectionCount() {
    final TreePath[] paths = myTree.getSelectionModel().getSelectionPaths();
    int count = 0;
    final List<ChangesBrowserNode> nodes = new ArrayList<>();

    for (final TreePath path : paths) {
      final ChangesBrowserNode node = (ChangesBrowserNode)path.getLastPathComponent();
      if (!node.isLeaf()) {
        nodes.add(node);
        count += node.getFileCount();
      }
    }

    for (TreePath path : paths) {
      final ChangesBrowserNode element = (ChangesBrowserNode)path.getLastPathComponent();
      boolean child = false;
      for (final ChangesBrowserNode node : nodes) {
        if (node.isNodeChild(element)) {
          child = true;
          break;
        }
      }

      if (!child) {
        if (element.isLeaf()) count++;
      }
      else if (!element.isLeaf()) {
        count -= element.getFileCount();
      }
    }
    return count;
  }
}