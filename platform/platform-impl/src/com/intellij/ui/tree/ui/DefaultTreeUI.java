// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.AbstractLayoutCache;
import javax.swing.tree.TreePath;

import static com.intellij.openapi.util.SystemInfo.isMac;
import static com.intellij.openapi.util.registry.Registry.is;

@SuppressWarnings("unused")
public final class DefaultTreeUI extends BasicTreeUI {
  private static final Logger LOG = Logger.getInstance(DefaultTreeUI.class);

  private static void logStackTrace(@NotNull String message) {
    if (LOG.isDebugEnabled()) LOG.warn(new IllegalStateException(message));
  }

  private static boolean isEventDispatchThread() {
    if (EventQueue.isDispatchThread()) return true;
    logStackTrace("unexpected thread");
    return false;
  }

  private static int getDepth(@NotNull JTree tree, @NotNull TreePath path) {
    int depth = path.getPathCount();
    if (!tree.isRootVisible()) depth--;
    if (!tree.getShowsRootHandles()) depth--;
    return depth;
  }

  @NotNull
  private static Control.Painter getPainter(@NotNull JTree tree) {
    Object property = tree.getClientProperty(Control.Painter.class);
    if (property instanceof Control.Painter) return (Control.Painter)property;
    if (is("ide.tree.painter.classic.compact")) return ClassicPainter.COMPACT;
    if (is("ide.tree.painter.compact.default")) return CompactPainter.DEFAULT;
    return ClassicPainter.DEFAULT;
  }

  @Nullable
  private static Color getBackground(@NotNull JTree tree, @NotNull TreePath path, boolean selected) {
    if (selected) return UIUtil.getTreeSelectionBackground(tree.hasFocus());
    if (tree instanceof Tree) {
      Tree custom = (Tree)tree;
      if (custom.isFileColorsEnabled()) return custom.getFileColorForPath(path);
    }
    return null;
  }

  private static int getExpandedRow(@NotNull JTree tree) {
    if (tree instanceof Tree) {
      Tree custom = (Tree)tree;
      Collection<Integer> items = custom.getExpandableItemsHandler().getExpandedItems();
      if (!items.isEmpty()) return items.iterator().next();
    }
    return -1;
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass"})
  public static ComponentUI createUI(JComponent component) {
    return new DefaultTreeUI();
  }

  // non static

  private final Control control = new DefaultControl();

  private JTree getTree() {
    return super.tree; // TODO: tree ???
  }

  private boolean isLeaf(@NotNull TreePath path) {
    return super.treeModel.isLeaf(path.getLastPathComponent()); // TODO: treeModel ???
  }

  private boolean isValid(@Nullable JTree tree) {
    if (!isEventDispatchThread()) return false;
    if (tree != null && tree == getTree()) return true;
    logStackTrace(tree != null ? "unexpected tree" : "undefined tree");
    return false;
  }

  // ComponentUI

  @Override
  public void paint(Graphics g, JComponent c) {
    AbstractLayoutCache cache = treeState; // TODO: treeState ???
    if (cache == null) return;
    JTree tree = (JTree)c;
    if (!isValid(tree)) return;
    g = g.create();
    try {
      Rectangle paintBounds = g.getClipBounds();
      Insets insets = tree.getInsets();
      TreePath path = cache.getPathClosestTo(0, paintBounds.y - insets.top);
      int row = cache.getRowForPath(path);
      if (row >= 0) {
        Control.Painter painter = getPainter(tree);
        Rectangle buffer = new Rectangle();
        int expandedRow = getExpandedRow(tree);
        int maxPaintY = paintBounds.y + paintBounds.height;
        int viewportX = 0;
        int viewportWidth = tree.getWidth();
        int vsbWidth = 0;
        boolean hsbVisible = false;
        Container parent = tree.getParent();
        if (parent instanceof JViewport) {
          viewportX = -tree.getX();
          viewportWidth = parent.getWidth();
          parent = parent.getParent();
          if (parent instanceof JBScrollPane) {
            JBScrollPane pane = (JBScrollPane)parent;
            JScrollBar hsb = pane.getHorizontalScrollBar();
            if (hsb != null && hsb.isVisible()) hsbVisible = true;
            JScrollBar vsb = isMac ? null : pane.getVerticalScrollBar();
            if (vsb != null && vsb.isVisible() && !vsb.isOpaque()) vsbWidth = vsb.getWidth();
          }
        }
        while (path != null) {
          Rectangle bounds = cache.getBounds(path, buffer);
          if (bounds == null) break;
          bounds.y += insets.top;

          int depth = getDepth(tree, path);
          boolean leaf = isLeaf(path);
          boolean expanded = !leaf && cache.getExpandedState(path);
          boolean selected = tree.isRowSelected(row);
          boolean focused = tree.hasFocus();
          boolean lead = focused && row == getLeadSelectionRow();

          Color background = getBackground(tree, path, selected);
          if (background != null) {
            g.setColor(background);
            g.fillRect(viewportX, insets.top + bounds.y, viewportWidth, bounds.height);
          }
          int offset = painter.getRendererOffset(control, depth, leaf);
          painter.paint(g, insets.left, insets.top + bounds.y, offset, bounds.height, control, depth, leaf, expanded, selected);
          // TODO: editingComponent, editingRow ???
          if (editingComponent == null || editingRow != row) {
            int width = viewportX + viewportWidth - insets.left - offset - vsbWidth;
            if (width < bounds.width && (hsbVisible || expandedRow == row)) {
              width = bounds.width;
            }
            if (width > 0) {
              Object value = path.getLastPathComponent();
              // TODO: currentCellRenderer, rendererPane ???
              Component component = currentCellRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, lead);
              if (component != null) {
                if (background != null) component.setBackground(background);
                rendererPane.paintComponent(g, component, tree, insets.left + offset, bounds.y, width, bounds.height, true);
              }
            }
          }
          if ((bounds.y + bounds.height) >= maxPaintY) break;
          path = cache.getPathForRow(++row);
        }
      }
      paintDropLine(g);
      // TODO: rendererPane ???
      rendererPane.removeAll();
    }
    finally {
      g.dispose();
    }
  }

  // BasicTreeUI

  @Override
  protected void installKeyboardActions() {
    super.installKeyboardActions();
    ActionMap map = tree.getActionMap();
    TreeAction.SELECT_CHILD.putInto(map);
    TreeAction.SELECT_PARENT.putInto(map);
  }

  @Override
  protected boolean isLocationInExpandControl(TreePath path, int mouseX, int mouseY) {
    JTree tree = getTree();
    if (tree == null || path == null || isLeaf(path)) return false;
    int depth = getDepth(tree, path);
    Control.Painter painter = getPainter(tree);
    Insets insets = tree.getInsets();
    if (insets != null) mouseX -= insets.left;
    return painter.getControlOffset(control, depth, false) <= mouseX && mouseX < painter.getRendererOffset(control, depth, false);
  }

  @Override
  protected int getRowX(int row, int depth) {
    JTree tree = getTree();
    if (tree == null) return 0;
    TreePath path = getPathForRow(tree, row);
    if (path == null) return 0;
    return getPainter(tree).getRendererOffset(control, getDepth(tree, path), isLeaf(path));
  }

  @Override
  protected AbstractLayoutCache createLayoutCache() {
    return super.createLayoutCache();
  }

  // TreeUI

  @Override
  public Rectangle getPathBounds(JTree tree, TreePath path) {
    if (path == null || !isValid(tree)) return null;
    return super.getPathBounds(tree, path);
  }

  @Override
  public TreePath getPathForRow(JTree tree, int row) {
    if (!isValid(tree)) return null;
    return super.getPathForRow(tree, row);
  }

  @Override
  public int getRowForPath(JTree tree, TreePath path) {
    if (path == null || !isValid(tree)) return -1;
    return super.getRowForPath(tree, path);
  }

  @Override
  public int getRowCount(JTree tree) {
    if (!isValid(tree)) return 0;
    return super.getRowCount(tree);
  }

  @Override
  public TreePath getClosestPathForLocation(JTree tree, int x, int y) {
    if (!isValid(tree)) return null;
    return super.getClosestPathForLocation(tree, x, y);
  }

  @Override
  public boolean isEditing(JTree tree) {
    if (!isValid(tree)) return false;
    return super.isEditing(tree);
  }

  @Override
  public boolean stopEditing(JTree tree) {
    if (!isValid(tree)) return false;
    return super.stopEditing(tree);
  }

  @Override
  public void cancelEditing(JTree tree) {
    if (!isValid(tree)) return;
    super.cancelEditing(tree);
  }

  @Override
  public void startEditingAtPath(JTree tree, TreePath path) {
    if (path == null || !isValid(tree)) return;
    super.startEditingAtPath(tree, path);
  }

  @Override
  public TreePath getEditingPath(JTree tree) {
    if (!isValid(tree)) return null;
    return super.getEditingPath(tree);
  }
}
