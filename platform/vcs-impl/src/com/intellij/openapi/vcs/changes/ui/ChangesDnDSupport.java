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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.vcs.changes.ui.ChangesListView.getChanges;
import static com.intellij.openapi.vcs.changes.ui.ChangesListView.getVirtualFiles;

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
    Image image = DragImageFactory.createImage(myTree);
    return new DnDImage(image, new Point(-image.getWidth(null), -image.getHeight(null)));
  }

  @Nullable
  private DnDDragStartBean createDragStartBean(@NotNull DnDActionInfo info) {
    DnDDragStartBean result = null;

    if (info.isMove()) {
      Change[] changes = getChanges(myProject, myTree.getSelectionPaths());
      List<VirtualFile> unversionedFiles = getVirtualFiles(myTree.getSelectionPaths(), ChangesBrowserNode.UNVERSIONED_FILES_TAG);
      List<VirtualFile> ignoredFiles = getVirtualFiles(myTree.getSelectionPaths(), ChangesBrowserNode.IGNORED_FILES_TAG);

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
    if (!(attached instanceof ChangeListDragBean)) return false;

    final ChangeListDragBean dragBean = (ChangeListDragBean)attached;
    if (dragBean.getSourceComponent() != myTree) return false;
    dragBean.setTargetNode(null);

    RelativePoint dropPoint = aEvent.getRelativePoint();
    Point onTree = dropPoint.getPoint(myTree);
    final TreePath dropPath = myTree.getPathForLocation(onTree.x, onTree.y);

    if (dropPath == null) return false;

    ChangesBrowserNode dropNode = (ChangesBrowserNode)dropPath.getLastPathComponent();
    while (!((ChangesBrowserNode)dropNode.getParent()).isRoot()) {
      dropNode = (ChangesBrowserNode)dropNode.getParent();
    }

    if (!dropNode.canAcceptDrop(dragBean)) {
      return false;
    }

    final Rectangle tableCellRect = myTree.getPathBounds(new TreePath(dropNode.getPath()));
    if (fitsInBounds(tableCellRect)) {
      aEvent.setHighlighting(new RelativeRectangle(myTree, tableCellRect), DnDEvent.DropTargetHighlightingType.RECTANGLE);
    }

    aEvent.setDropPossible(true);
    dragBean.setTargetNode(dropNode);

    return false;
  }

  @Override
  public void drop(DnDEvent aEvent) {
    Object attached = aEvent.getAttachedObject();
    if (!(attached instanceof ChangeListDragBean)) return;

    final ChangeListDragBean dragBean = (ChangeListDragBean)attached;
    final ChangesBrowserNode changesBrowserNode = dragBean.getTargetNode();
    if (changesBrowserNode != null) {
      changesBrowserNode.acceptDrop(myChangeListManager, dragBean);
    }
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

  @SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
  private static class DragImageFactory {
    private static void drawSelection(JTable table, int column, Graphics g, final int width) {
      int y = 0;
      final int[] rows = table.getSelectedRows();
      final int height = table.getRowHeight();
      for (int row : rows) {
        final TableCellRenderer renderer = table.getCellRenderer(row, column);
        final Component component = renderer.getTableCellRendererComponent(table, table.getValueAt(row, column), false, false, row, column);
        g.translate(0, y);
        component.setBounds(0, 0, width, height);
        boolean wasOpaque = false;
        if (component instanceof JComponent) {
          final JComponent j = (JComponent)component;
          if (j.isOpaque()) wasOpaque = true;
          j.setOpaque(false);
        }
        component.paint(g);
        if (wasOpaque) {
          ((JComponent)component).setOpaque(true);
        }
        y += height;
        g.translate(0, -y);
      }
    }

    private static void drawSelection(JTree tree, Graphics g, final int width) {
      int y = 0;
      final int[] rows = tree.getSelectionRows();
      final int height = tree.getRowHeight();
      for (int row : rows) {
        final TreeCellRenderer renderer = tree.getCellRenderer();
        final Object value = tree.getPathForRow(row).getLastPathComponent();
        if (value == null) continue;
        final Component component = renderer.getTreeCellRendererComponent(tree, value, false, false, false, row, false);
        if (component.getFont() == null) {
          component.setFont(tree.getFont());
        }
        g.translate(0, y);
        component.setBounds(0, 0, width, height);
        boolean wasOpaque = false;
        if (component instanceof JComponent) {
          final JComponent j = (JComponent)component;
          if (j.isOpaque()) wasOpaque = true;
          j.setOpaque(false);
        }
        component.paint(g);
        if (wasOpaque) {
          ((JComponent)component).setOpaque(true);
        }
        y += height;
        g.translate(0, -y);
      }
    }

    public static Image createImage(final JTable table, int column) {
      final int height = Math.max(20, Math.min(100, table.getSelectedRowCount() * table.getRowHeight()));
      final int width = table.getColumnModel().getColumn(column).getWidth();

      final BufferedImage image = UIUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2 = (Graphics2D)image.getGraphics();

      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));

      drawSelection(table, column, g2, width);
      return image;
    }

    public static Image createImage(final JTree tree) {
      final TreeSelectionModel model = tree.getSelectionModel();
      final TreePath[] paths = model.getSelectionPaths();

      int count = 0;
      final java.util.List<ChangesBrowserNode> nodes = new ArrayList<ChangesBrowserNode>();
      for (final TreePath path : paths) {
        final ChangesBrowserNode node = (ChangesBrowserNode)path.getLastPathComponent();
        if (!node.isLeaf()) {
          nodes.add(node);
          count += node.getCount();
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
          count -= element.getCount();
        }
      }

      // TODO: Looks like DnDAwareTree.getDragImage() could be utilized here
      final JLabel label = new JLabel(VcsBundle.message("changes.view.dnd.label", count));
      label.setOpaque(true);
      label.setForeground(tree.getForeground());
      label.setBackground(tree.getBackground());
      label.setFont(tree.getFont());
      label.setSize(label.getPreferredSize());
      final BufferedImage image = UIUtil.createImage(label.getWidth(), label.getHeight(), BufferedImage.TYPE_INT_ARGB);

      Graphics2D g2 = (Graphics2D)image.getGraphics();
      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
      label.paint(g2);
      g2.dispose();

      return image;
    }
  }
}