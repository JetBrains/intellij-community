/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.util.ui.tree;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/**
* @author Konstantin Bulenkov
*/
public class MacTreeUI extends BasicTreeUI {
  private static final Color DECORATED_ROW_BG_COLOR = new Color(242, 245, 249);
  @NonNls public static final String SOURCE_LIST_CLIENT_PROPERTY = "mac.ui.source.list";
  @NonNls public static final String STRIPED_CLIENT_PROPERTY = "mac.ui.striped";

  private static final Icon TREE_COLLAPSED_ICON = UIUtil.getTreeCollapsedIcon();
  private static final Icon TREE_EXPANDED_ICON = UIUtil.getTreeExpandedIcon();
  private static final Icon TREE_SELECTED_COLLAPSED_ICON = AllIcons.Mac.Tree_white_right_arrow;
  private static final Icon TREE_SELECTED_EXPANDED_ICON = AllIcons.Mac.Tree_white_down_arrow;

  private static final Border LIST_BACKGROUND_PAINTER = (Border)UIManager.get("List.sourceListBackgroundPainter");
  private static final Border LIST_SELECTION_BACKGROUND_PAINTER = (Border)UIManager.get("List.sourceListSelectionBackgroundPainter");
  private static final Border LIST_FOCUSED_SELECTION_BACKGROUND_PAINTER =
    (Border)UIManager.get("List.sourceListFocusedSelectionBackgroundPainter");

  private boolean myWideSelection;
  private boolean myAlwaysPaintRowBackground;
  private boolean myOldRepaintAllRowValue;

  public MacTreeUI() {
    this(true, true);
  }

  public MacTreeUI(final boolean wideSelection, boolean alwaysPaintRowBackground) {
    myWideSelection = wideSelection;
    myAlwaysPaintRowBackground = alwaysPaintRowBackground;
  }

  private final MouseListener mySelectionListener = new MouseAdapter() {
    @Override
    public void mousePressed(@NotNull final MouseEvent e) {
      final JTree tree = (JTree)e.getSource();
      if (SwingUtilities.isLeftMouseButton(e) && !e.isPopupTrigger()) {
        // if we can't stop any ongoing editing, do nothing
        if (isEditing(tree) && tree.getInvokesStopCellEditing() && !stopEditing(tree)) {
          return;
        }

        final TreePath pressedPath = getClosestPathForLocation(tree, e.getX(), e.getY());
        if (pressedPath != null) {
          Rectangle bounds = getPathBounds(tree, pressedPath);

          if (e.getY() >= bounds.y + bounds.height) {
            return;
          }

          if (bounds.contains(e.getPoint()) || isLocationInExpandControl(pressedPath, e.getX(), e.getY())) {
            return;
          }

          if (tree.getDragEnabled() || !startEditing(pressedPath, e)) {
            selectPathForEvent(pressedPath, e);
          }
        }
      }
    }
  };

  @Override
  protected void completeUIInstall() {
    super.completeUIInstall();

    myOldRepaintAllRowValue = UIManager.getBoolean("Tree.repaintWholeRow");
    UIManager.put("Tree.repaintWholeRow", true);

    tree.setShowsRootHandles(true);
    tree.addMouseListener(mySelectionListener);

    //final InputMap inputMap = tree.getInputMap(JComponent.WHEN_FOCUSED);
    //inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearSelection");
  }

  @Override
  public void uninstallUI(JComponent c) {
    super.uninstallUI(c);

    UIManager.put("Tree.repaintWholeRow", myOldRepaintAllRowValue);
    c.removeMouseListener(mySelectionListener);
  }

  @Override
  protected void installKeyboardActions() {
    super.installKeyboardActions();

    if (Boolean.TRUE.equals(tree.getClientProperty("MacTreeUi.actionsInstalled"))) return;

    tree.putClientProperty("MacTreeUi.actionsInstalled", Boolean.TRUE);

    final InputMap inputMap = tree.getInputMap(JComponent.WHEN_FOCUSED);
    inputMap.put(KeyStroke.getKeyStroke("pressed LEFT"), "collapse_or_move_up");
    inputMap.put(KeyStroke.getKeyStroke("pressed RIGHT"), "expand");

    final ActionMap actionMap = tree.getActionMap();

    final Action expandAction = actionMap.get("expand");
    if (expandAction != null) {
      actionMap.put("expand", new TreeUIAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          final Object source = e.getSource();
          if (source instanceof JTree) {
            JTree tree = (JTree)source;
            int selectionRow = tree.getLeadSelectionRow();
            if (selectionRow != -1) {
              TreePath selectionPath = tree.getPathForRow(selectionRow);
              if (selectionPath != null) {
                boolean leaf = tree.getModel().isLeaf(selectionPath.getLastPathComponent());
                int toSelect = -1;
                int toScroll = -1;
                if (!leaf && tree.isExpanded(selectionRow)) {
                  if (selectionRow + 1 < tree.getRowCount()) {
                    toSelect = selectionRow + 1;
                    toScroll = toSelect;
                  }
                } else if (leaf) {
                  toScroll = selectionRow;
                }

                if (toSelect != -1) {
                  tree.setSelectionInterval(toSelect, toSelect);
                }

                if (toScroll != -1) {
                  tree.scrollRowToVisible(toScroll);
                }

                if (toSelect != -1 || toScroll != -1) return;
              }
            }
          }


          expandAction.actionPerformed(e);
        }
      });
    }

    actionMap.put("collapse_or_move_up", new TreeUIAction() {
      public void actionPerformed(final ActionEvent e) {
        final Object source = e.getSource();
        if (source instanceof JTree) {
          JTree tree = (JTree)source;
          int selectionRow = tree.getLeadSelectionRow();
          if (selectionRow == -1) return;

          TreePath selectionPath = tree.getPathForRow(selectionRow);
          if (selectionPath == null) return;

          if (tree.getModel().isLeaf(selectionPath.getLastPathComponent()) || tree.isCollapsed(selectionRow)) {
            final TreePath parentPath = tree.getPathForRow(selectionRow).getParentPath();
            if (parentPath != null) {
              if (parentPath.getParentPath() != null || tree.isRootVisible()) {
                final int parentRow = tree.getRowForPath(parentPath);
                tree.scrollRowToVisible(parentRow);
                tree.setSelectionInterval(parentRow, parentRow);
              }
            }
          }
          else {
            tree.collapseRow(selectionRow);
          }
        }
      }
    });
  }

  private abstract static class TreeUIAction extends AbstractAction implements UIResource {
  }

  @Override
  protected void paintHorizontalPartOfLeg(final Graphics g,
                                          final Rectangle clipBounds,
                                          final Insets insets,
                                          final Rectangle bounds,
                                          final TreePath path,
                                          final int row,
                                          final boolean isExpanded,
                                          final boolean hasBeenExpanded,
                                          final boolean isLeaf) {

  }

  @Override
  protected boolean isToggleSelectionEvent(MouseEvent e) {
    return SwingUtilities.isLeftMouseButton(e) && (SystemInfo.isMac ? e.isMetaDown() : e.isControlDown()) && !e.isPopupTrigger();
  }

  @Override
  protected void paintVerticalPartOfLeg(final Graphics g, final Rectangle clipBounds, final Insets insets, final TreePath path) {
  }

  @Override
  protected void paintHorizontalLine(Graphics g, JComponent c, int y, int left, int right) {
  }

  public boolean isWideSelection() {
    return myWideSelection;
  }

  @Override
  protected void paintRow(final Graphics g,
                          final Rectangle clipBounds,
                          final Insets insets,
                          final Rectangle bounds,
                          final TreePath path,
                          final int row,
                          final boolean isExpanded,
                          final boolean hasBeenExpanded,
                          final boolean isLeaf) {
    final int containerWidth = tree.getParent() instanceof JViewport ? tree.getParent().getWidth() : tree.getWidth();
    final int xOffset = tree.getParent() instanceof JViewport ? ((JViewport)tree.getParent()).getViewPosition().x : 0;

    if (path != null && myWideSelection) {
      boolean selected = tree.isPathSelected(path);
      Graphics2D rowGraphics = (Graphics2D)g.create();
      rowGraphics.setClip(clipBounds);

      final Object sourceList = tree.getClientProperty(SOURCE_LIST_CLIENT_PROPERTY);
      Color background = tree.getBackground();

      if ((row % 2) == 0 && Boolean.TRUE.equals(tree.getClientProperty(STRIPED_CLIENT_PROPERTY))) {
        background = UIUtil.getDecoratedRowColor();
      }

      if (sourceList != null && (Boolean)sourceList) {
        if (selected) {
          if (tree.hasFocus()) {
            LIST_FOCUSED_SELECTION_BACKGROUND_PAINTER.paintBorder(tree, rowGraphics, xOffset, bounds.y, containerWidth, bounds.height);
          }
          else {
            LIST_SELECTION_BACKGROUND_PAINTER.paintBorder(tree, rowGraphics, xOffset, bounds.y, containerWidth, bounds.height);
          }
        }
        else if (myAlwaysPaintRowBackground) {
          rowGraphics.setColor(background);
          rowGraphics.fillRect(xOffset, bounds.y, containerWidth, bounds.height);
        }
      }
      else {
        Color bg = tree.hasFocus() ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeUnfocusedSelectionBackground();
        if (!selected) {
          bg = background;
        }

        if (myAlwaysPaintRowBackground || selected) {
          rowGraphics.setColor(bg);
          rowGraphics.fillRect(xOffset, bounds.y, containerWidth, bounds.height - 1);
        }
      }

      if (shouldPaintExpandControl(path, row, isExpanded, hasBeenExpanded, isLeaf)) {
        paintExpandControl(rowGraphics, bounds, insets, bounds, path, row, isExpanded, hasBeenExpanded, isLeaf);
      }

      super.paintRow(rowGraphics, clipBounds, insets, bounds, path, row, isExpanded, hasBeenExpanded, isLeaf);
      rowGraphics.dispose();
    }
    else {
      super.paintRow(g, clipBounds, insets, bounds, path, row, isExpanded, hasBeenExpanded, isLeaf);
    }
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    if (myWideSelection) {
      final int containerWidth = tree.getParent() instanceof JViewport ? tree.getParent().getWidth() : tree.getWidth();
      final int xOffset = tree.getParent() instanceof JViewport ? ((JViewport)tree.getParent()).getViewPosition().x : 0;
      final Rectangle bounds = g.getClipBounds();

      // draw background for the given clip bounds
      final Object sourceList = tree.getClientProperty(SOURCE_LIST_CLIENT_PROPERTY);
      if (sourceList != null && (Boolean)sourceList) {
        Graphics2D backgroundGraphics = (Graphics2D)g.create();
        backgroundGraphics.setClip(xOffset, bounds.y, containerWidth, bounds.height);
        LIST_BACKGROUND_PAINTER.paintBorder(tree, backgroundGraphics, xOffset, bounds.y, containerWidth, bounds.height);
        backgroundGraphics.dispose();
      }
    }

    super.paint(g, c);
  }

  @Override
  protected CellRendererPane createCellRendererPane() {
    return new CellRendererPane() {
      @Override
      public void paintComponent(Graphics g, Component c, Container p, int x, int y, int w, int h, boolean shouldValidate) {
        if (c instanceof JComponent && myWideSelection) {
          ((JComponent)c).setOpaque(false);
        }

        super.paintComponent(g, c, p, x, y, w, h, shouldValidate);
      }
    };
  }

  @Override
  protected void paintExpandControl(Graphics g,
                                    Rectangle clipBounds,
                                    Insets insets,
                                    Rectangle bounds,
                                    TreePath path,
                                    int row,
                                    boolean isExpanded,
                                    boolean hasBeenExpanded,
                                    boolean isLeaf) {
    boolean isPathSelected = tree.getSelectionModel().isPathSelected(path);
    boolean dark = UIUtil.isUnderDarcula();

    Icon expandIcon = (isPathSelected && tree.hasFocus()) || dark ? TREE_SELECTED_EXPANDED_ICON
                                                        : TREE_EXPANDED_ICON;
    Icon collapseIcon = (isPathSelected && tree.hasFocus()) || dark? TREE_SELECTED_COLLAPSED_ICON
                                                          : TREE_COLLAPSED_ICON;


    if (!isLeaf(row)) {
      setExpandedIcon(expandIcon);
      setCollapsedIcon(collapseIcon);
    }

    super.paintExpandControl(g, clipBounds, insets, bounds, path, row, isExpanded, hasBeenExpanded, isLeaf);
  }
}
