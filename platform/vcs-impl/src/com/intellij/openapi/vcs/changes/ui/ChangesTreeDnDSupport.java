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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.awt.RelativePoint;
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

public abstract class ChangesTreeDnDSupport implements DnDDropHandler, DnDTargetChecker {
  @NotNull protected final ChangesTree myTree;

  public ChangesTreeDnDSupport(@NotNull ChangesTree tree) {
    myTree = tree;
  }

  public void install(@NotNull Disposable disposable) {
    DnDSupport.createBuilder(myTree)
      .setTargetChecker(this)
      .setDropHandler(this)
      .setImageProvider(this::createDraggedImage)
      .setBeanProvider(this::createDragStartBean)
      .setDisposableParent(disposable)
      .install();
  }

  @NotNull
  protected DnDImage createDraggedImage(@NotNull DnDActionInfo info) {
    int count = getSelectionCount(myTree);
    String imageText = count + " " + StringUtil.pluralize("file", count);
    return createDragImage(myTree, imageText);
  }

  @Nullable
  protected abstract DnDDragStartBean createDragStartBean(@NotNull DnDActionInfo info);

  protected abstract boolean canHandleDropEvent(@NotNull DnDEvent aEvent, @NotNull ChangesBrowserNode<?> dropNode);

  @Override
  public boolean update(DnDEvent aEvent) {
    aEvent.hideHighlighter();
    aEvent.setDropPossible(false, "");

    ChangesBrowserNode<?> dropNode = getDropRootNode(myTree, aEvent);

    boolean canHandle = dropNode != null && canHandleDropEvent(aEvent, dropNode);
    if (!canHandle) return true;

    highlightDropNode(aEvent, dropNode);
    aEvent.setDropPossible(true);

    return false;
  }

  private void highlightDropNode(@NotNull DnDEvent aEvent, @NotNull ChangesBrowserNode<?> dropNode) {
    final Rectangle tableCellRect = myTree.getPathBounds(new TreePath(dropNode.getPath()));
    if (tableCellRect != null && fitsInBounds(tableCellRect)) {
      aEvent.setHighlighting(new RelativeRectangle(myTree, tableCellRect), DnDEvent.DropTargetHighlightingType.RECTANGLE);
    }
  }

  @Nullable
  public static ChangesBrowserNode<?> getDropRootNode(@NotNull ChangesTree tree, @NotNull DnDEvent event) {
    RelativePoint dropPoint = event.getRelativePoint();
    Point onTree = dropPoint.getPoint(tree);
    final TreePath dropPath = tree.getPathForLocation(onTree.x, onTree.y);

    if (dropPath == null) return null;

    ChangesBrowserNode<?> dropNode = (ChangesBrowserNode<?>)dropPath.getLastPathComponent();
    while (!dropNode.getParent().isRoot()) {
      dropNode = dropNode.getParent();
    }
    return dropNode;
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

  @NotNull
  public static DnDImage createDragImage(@NotNull Tree tree, @NotNull String imageText) {
    Image image = DnDAwareTree.getDragImage(tree, imageText, null).getFirst();
    return new DnDImage(image, new Point(-image.getWidth(null), -image.getHeight(null)));
  }

  public static int getSelectionCount(@NotNull ChangesTree tree) {
    TreePath[] paths = tree.getSelectionModel().getSelectionPaths();

    List<ChangesBrowserNode<?>> parents = new ArrayList<>();
    for (TreePath path : paths) {
      ChangesBrowserNode<?> node = (ChangesBrowserNode<?>)path.getLastPathComponent();
      if (!node.isLeaf()) {
        parents.add(node);
      }
    }

    int count = 0;

    outer:
    for (TreePath path : paths) {
      ChangesBrowserNode<?> node = (ChangesBrowserNode<?>)path.getLastPathComponent();
      for (ChangesBrowserNode<?> parent : parents) {
        if (isChildNode(parent, node)) continue outer;
      }

      count += node.getFileCount();
    }
    return count;
  }

  private static boolean isChildNode(ChangesBrowserNode<?> parent, ChangesBrowserNode<?> node) {
    ChangesBrowserNode<?> nodeParent = node.getParent();
    while (nodeParent != null) {
      if (nodeParent == parent) return true;
      nodeParent = nodeParent.getParent();
    }
    return false;
  }
}
