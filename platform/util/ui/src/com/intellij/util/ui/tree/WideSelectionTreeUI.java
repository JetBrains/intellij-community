// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.tree;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.MouseEventAdapter;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.TreeUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.lang.reflect.Method;
import java.util.Set;

import static com.intellij.util.ReflectionUtil.getMethod;
import static com.intellij.util.containers.ContainerUtil.newConcurrentSet;

/**
* @author Konstantin Bulenkov
*/
public class WideSelectionTreeUI extends BasicTreeUI {
  public static final String TREE_TABLE_TREE_KEY = "TreeTableTree";

  @NonNls public static final String SOURCE_LIST_CLIENT_PROPERTY = "mac.ui.source.list";
  @NonNls public static final String STRIPED_CLIENT_PROPERTY = "mac.ui.striped";

  private static final Border LIST_BACKGROUND_PAINTER = UIManager.getBorder("List.sourceListBackgroundPainter");
  private static final Border LIST_SELECTION_BACKGROUND_PAINTER = UIManager.getBorder("List.sourceListSelectionBackgroundPainter");
  private static final Border LIST_FOCUSED_SELECTION_BACKGROUND_PAINTER = UIManager.getBorder("List.sourceListFocusedSelectionBackgroundPainter");

  private static final Logger LOG = Logger.getInstance(WideSelectionTreeUI.class);
  private static final Set<String> LOGGED_RENDERERS = newConcurrentSet();

  @NotNull private final Condition<? super Integer> myWideSelectionCondition;
  private final boolean myWideSelection;
  private boolean myOldRepaintAllRowValue;
  private boolean myForceDontPaintLines = false;
  private static final boolean mySkinny = false;

  private static final TreeUIAction EXPAND_OR_SELECT_NEXT = new TreeUIAction() {
    @Override
    public void actionPerformed(ActionEvent event) {
      Object source = event.getSource();
      if (source instanceof JTree) {
        JTree tree = (JTree)source;
        TreePath path = tree.getLeadSelectionPath();
        if (path != null) {
          if (tree.isExpanded(path) || tree.getModel().isLeaf(path.getLastPathComponent())) {
            int row = tree.getRowForPath(path);
            path = row < 0 ? null : tree.getPathForRow(row + 1);
            if (path != null) {
              tree.setSelectionPath(path);
              tree.scrollPathToVisible(path);
            }
          }
          else {
            tree.expandPath(path);
          }
        }
      }
    }
  };

  private static final TreeUIAction COLLAPSE_OR_SELECT_PREVIOUS = new TreeUIAction() {
    @Override
    public void actionPerformed(ActionEvent event) {
      Object source = event.getSource();
      if (source instanceof JTree) {
        JTree tree = (JTree)source;
        TreePath path = tree.getLeadSelectionPath();
        if (path != null) {
          if (tree.isExpanded(path)) {
            tree.collapsePath(path);
          }
          else {
            TreePath parent = path.getParentPath();
            if (parent != null) {
              if (!tree.isRootVisible() && null == parent.getParentPath()) {
                int row = tree.getRowForPath(path);
                parent = row < 1 ? null : tree.getPathForRow(row - 1);
              }
              if (parent != null) {
                tree.setSelectionPath(parent);
                tree.scrollPathToVisible(parent);
              }
            }
          }
        }
      }
    }
  };

  public WideSelectionTreeUI() {
    this(true, Conditions.alwaysTrue());
  }

  /**
   * Creates new {@code WideSelectionTreeUI} object.
   *
   * @param wideSelection           flag that determines if wide selection should be used
   * @param wideSelectionCondition  strategy that determine if wide selection should be used for a target row (it's zero-based index
   *                                is given to the condition as an argument)
   */
  public WideSelectionTreeUI(final boolean wideSelection, @NotNull Condition<? super Integer> wideSelectionCondition) {
    myWideSelection = wideSelection;
    myWideSelectionCondition = wideSelectionCondition;
  }

  @Override
  public int getRightChildIndent() {
    return isCustomIndent() ? getCustomIndent() : super.getRightChildIndent();
  }

  public boolean isCustomIndent() {
    return getCustomIndent() > 0;
  }

  protected int getCustomIndent() {
    return JBUIScale.scale(Registry.intValue("ide.ui.tree.indent"));
  }

  @Override
  protected MouseListener createMouseListener() {
    return new MouseEventAdapter<MouseListener>(super.createMouseListener()) {
      @Override
      public void mouseDragged(MouseEvent event) {
        JTree tree = (JTree)event.getSource();
        Object property = tree.getClientProperty("DnD Source"); // DnDManagerImpl.SOURCE_KEY
        if (property == null) {
          super.mouseDragged(event); // use Swing-based DnD only if custom DnD is not set
        }
      }

      @NotNull
      @Override
      protected MouseEvent convert(@NotNull MouseEvent event) {
        if (!event.isConsumed() && SwingUtilities.isLeftMouseButton(event)) {
          int x = event.getX();
          int y = event.getY();
          JTree tree = (JTree)event.getSource();
          if (tree.isEnabled()) {
            TreePath path = getClosestPathForLocation(tree, x, y);
            if (path != null && !isLocationInExpandControl(path, x, y)) {
              Rectangle bounds = getPathBounds(tree, path);
              if (bounds != null && bounds.y <= y && y <= (bounds.y + bounds.height)) {
                x = Math.max(bounds.x, Math.min(x, bounds.x + bounds.width - 1));
                if (x != event.getX()) {
                  event = convert(event, tree, x, y);
                }
              }
            }
          }
        }
        return event;
      }
    };
  }

  @Override
  protected void completeUIInstall() {
    super.completeUIInstall();

    myOldRepaintAllRowValue = UIManager.getBoolean("Tree.repaintWholeRow");
    UIManager.put("Tree.repaintWholeRow", true);

    tree.setShowsRootHandles(true);
  }

  @Override
  public void uninstallUI(JComponent c) {
    super.uninstallUI(c);

    UIManager.put("Tree.repaintWholeRow", myOldRepaintAllRowValue);
  }

  @Override
  protected void installKeyboardActions() {
    super.installKeyboardActions();
    ActionMap map = tree.getActionMap();
    map.put("selectChild", EXPAND_OR_SELECT_NEXT);
    map.put("selectParent", COLLAPSE_OR_SELECT_PREVIOUS);
  }

  @Deprecated
  public void setForceDontPaintLines() {
    myForceDontPaintLines = true;
  }

  private abstract static class TreeUIAction extends AbstractAction implements UIResource {
  }

  @Override
  protected int getRowX(int row, int depth) {
    if (isCustomIndent()) {
      int off = tree.isRootVisible() ? 8 : 0;
      return 8 * depth + 8 + off;
    } else {
      return super.getRowX(row, depth);
    }
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
    if (shouldPaintLines()) {
      super.paintHorizontalPartOfLeg(g, clipBounds, insets, bounds, path, row, isExpanded, hasBeenExpanded, isLeaf);
    }
  }

  private boolean shouldPaintLines() {
    if (UIUtil.isUnderAquaBasedLookAndFeel() || UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF()) {
      return false;
    }
    return myForceDontPaintLines || !"None".equals(tree.getClientProperty("JTree.lineStyle"));
  }

  @Override
  protected boolean isToggleSelectionEvent(MouseEvent e) {
    return UIUtil.isToggleListSelectionEvent(e);
  }

  @Override
  protected void paintVerticalPartOfLeg(final Graphics g, final Rectangle clipBounds, final Insets insets, final TreePath path) {
    if (shouldPaintLines()) {
      super.paintVerticalPartOfLeg(g, clipBounds, insets, path);
    }
  }

  @Override
  protected void paintVerticalLine(Graphics g, JComponent c, int x, int top, int bottom) {
    if (shouldPaintLines()) {
      super.paintVerticalLine(g, c, x, top, bottom);
    }
  }

  public boolean isWideSelection() {
    return myWideSelection;
  }

  public static boolean isWideSelection(@NotNull JTree tree) {
    TreeUI ui = tree.getUI();
    return ui instanceof WideSelectionTreeUI && ((WideSelectionTreeUI)ui).isWideSelection() ||
           ui != null && ui.getClass().getName().equals("com.intellij.ui.tree.ui.DefaultTreeUI");
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
        else if (myWideSelectionCondition.value(row)) {
          rowGraphics.setColor(background);
          rowGraphics.fillRect(xOffset, bounds.y, containerWidth, bounds.height);
        }
      }
      else {
        if (selected && (UIUtil.isUnderAquaBasedLookAndFeel() || UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF())) {
          Color bg = getSelectionBackground(tree, true);

          if (myWideSelectionCondition.value(row)) {
            rowGraphics.setColor(bg);
            rowGraphics.fillRect(xOffset, bounds.y, containerWidth, bounds.height);
          }
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
    if (myWideSelection && !UIUtil.isUnderAquaBasedLookAndFeel() && !UIUtil.isUnderDarcula() && !UIUtil.isUnderIntelliJLaF()) {
      paintSelectedRows(g, ((JTree)c));
    }
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

  protected void paintSelectedRows(Graphics g, JTree tr) {
    final Rectangle rect = tr.getVisibleRect();
    final int firstVisibleRow = tr.getClosestRowForLocation(rect.x, rect.y);
    final int lastVisibleRow = tr.getClosestRowForLocation(rect.x, rect.y + rect.height);

    for (int row = firstVisibleRow; row <= lastVisibleRow; row++) {
      if (tr.getSelectionModel().isRowSelected(row) && myWideSelectionCondition.value(row)) {
          final Rectangle bounds = tr.getRowBounds(row);
          Color color = getSelectionBackground(tr, false);
          if (color != null) {
            g.setColor(color);
            g.fillRect(0, bounds.y, tr.getWidth(), bounds.height);
          }
      }
    }
  }

  @Override
  protected CellRendererPane createCellRendererPane() {
    return new CellRendererPane() {
      @Override
      public void paintComponent(Graphics g, Component c, Container p, int x, int y, int w, int h, boolean shouldValidate) {
        TreeCellRenderer renderer = currentCellRenderer;
        if (c != null && renderer != null && LOG.isDebugEnabled()) {
          Method method = getMethod(c.getClass(), "validate");
          Class<?> type = method == null ? null : method.getDeclaringClass();
          if (Component.class.equals(type) || Container.class.equals(type)) {
            String name = renderer.getClass().getName();
            if (LOGGED_RENDERERS.add(name)) LOG.debug("suspicious renderer: " + name);
          }
        }
        if (c instanceof JComponent && myWideSelection) {
          if (c.isOpaque()) {
            ((JComponent)c).setOpaque(false);
          }
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
    if (!isLeaf(row)) {
      setExpandedIcon(UIUtil.getTreeNodeIcon(true, isPathSelected, tree.hasFocus()));
      setCollapsedIcon(UIUtil.getTreeNodeIcon(false, isPathSelected, tree.hasFocus()));
    }

    super.paintExpandControl(g, clipBounds, insets, bounds, path, row, isExpanded, hasBeenExpanded, isLeaf);
  }

  @Nullable
  private static Color getSelectionBackground(@NotNull JTree tree, boolean checkProperty) {
    Object property = tree.getClientProperty(TREE_TABLE_TREE_KEY);
    if (property instanceof JTable) {
      return ((JTable)property).getSelectionBackground();
    }
    boolean selection = tree.hasFocus();
    if (!selection && checkProperty) {
      selection = Boolean.TRUE.equals(property);
    }
    return UIUtil.getTreeSelectionBackground(selection);
  }

  public void invalidateNodeSizes() {
    treeState.invalidateSizes();
  }
}
