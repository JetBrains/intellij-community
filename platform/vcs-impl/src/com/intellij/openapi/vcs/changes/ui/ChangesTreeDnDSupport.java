// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.dnd.*;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nls;
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
    String imageText = VcsBundle.message("vcs.dnd.image.text.n.files", count);
    return createDragImage(myTree, imageText);
  }

  @Nullable
  protected abstract DnDDragStartBean createDragStartBean(@NotNull DnDActionInfo info);

  protected abstract boolean canHandleDropEvent(@NotNull DnDEvent aEvent, @Nullable ChangesBrowserNode<?> dropNode);

  @Override
  public boolean update(DnDEvent aEvent) {
    aEvent.hideHighlighter();
    aEvent.setDropPossible(false, "");

    ChangesBrowserNode<?> dropNode = getDropNode(aEvent);

    boolean canHandle = canHandleDropEvent(aEvent, dropNode);
    if (!canHandle) return true;

    highlightDropNode(aEvent, dropNode);
    aEvent.setDropPossible(true);

    return false;
  }

  @Nullable
  protected ChangesBrowserNode<?> getDropNode(DnDEvent aEvent) {
    return getDropRootNode(myTree, aEvent);
  }

  private void highlightDropNode(@NotNull DnDEvent aEvent, @Nullable ChangesBrowserNode<?> dropNode) {
    final Rectangle tableCellRect;
    if (dropNode == null) {
      if (myTree.getRowCount() == 0) {
        tableCellRect = new Rectangle(0, 0, JBUI.scale(300), JBUI.scale(25));
      }
      else {
        Rectangle lastRowRect = myTree.getRowBounds(myTree.getRowCount() - 1);
        int y = lastRowRect.y + lastRowRect.height;
        tableCellRect = new Rectangle(0, y, JBUI.scale(300), lastRowRect.height);
      }
    }
    else {
      tableCellRect = myTree.getPathBounds(new TreePath(dropNode.getPath()));
    }
    if (tableCellRect != null && fitsInBounds(tableCellRect)) {
      aEvent.setHighlighting(new RelativeRectangle(myTree, tableCellRect), DnDEvent.DropTargetHighlightingType.RECTANGLE);
    }
  }

  @Nullable
  public static ChangesBrowserNode<?> getDropRootNode(@NotNull ChangesTree tree, @NotNull DnDEvent event) {
    RelativePoint dropPoint = event.getRelativePoint();
    Point onTree = dropPoint.getPoint(tree);
    TreePath dropPath = TreeUtil.getPathForLocation(tree, onTree.x, onTree.y);
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
    JScrollPane pane = ComponentUtil.getScrollPane(myTree);
    if (pane != null) {
      Rectangle rectangle = SwingUtilities.convertRectangle(myTree, rect, pane.getParent());
      return pane.getBounds().contains(rectangle);
    }
    return true;
  }

  @NotNull
  public static DnDImage createDragImage(@NotNull Tree tree, @NotNull @Nls String imageText) {
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
