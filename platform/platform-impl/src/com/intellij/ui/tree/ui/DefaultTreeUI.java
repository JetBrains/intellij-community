// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ColoredItem;
import com.intellij.openapi.util.Key;
import com.intellij.ui.BackgroundSupplier;
import com.intellij.ui.DirtyUI;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.hover.TreeHoverListener;
import com.intellij.ui.render.RenderingHelper;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.TreePathBackgroundSupplier;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MouseEventAdapter;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.AbstractLayoutCache;
import javax.swing.tree.FixedHeightLayoutCache;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.VariableHeightLayoutCache;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.openapi.util.SystemInfo.isMac;
import static com.intellij.openapi.util.registry.Registry.intValue;
import static com.intellij.openapi.util.registry.Registry.is;
import static com.intellij.ui.ColorUtil.isDark;
import static com.intellij.ui.paint.RectanglePainter.DRAW;
import static com.intellij.ui.paint.RectanglePainter.FILL;
import static com.intellij.util.EditSourceOnDoubleClickHandler.isExpandPreferable;
import static com.intellij.util.ReflectionUtil.getMethod;
import static com.intellij.util.containers.ContainerUtil.createWeakSet;

@DirtyUI
public final class DefaultTreeUI extends BasicTreeUI {
  @ApiStatus.Internal
  public static final Key<Boolean> LARGE_MODEL_ALLOWED = Key.create("allows to use large model (only for synchronous tree models)");
  @ApiStatus.Internal
  public static final Key<Boolean> AUTO_EXPAND_ALLOWED = Key.create("allows to expand a single child node automatically in tests");
  private static final Logger LOG = Logger.getInstance(DefaultTreeUI.class);
  private static final Collection<Class<?>> SUSPICIOUS = createWeakSet();

  @NotNull
  private static Control.Painter getPainter(@NotNull JTree tree) {
    Control.Painter painter = ComponentUtil.getClientProperty(tree, Control.Painter.KEY);
    if (painter != null) return painter;
    // painter is not specified for the given tree
    Application application = getApplication();
    if (application != null) {
      painter = application.getUserData(Control.Painter.KEY);
      if (painter != null) return painter;
      // painter is not specified for the whole application
    }
    UISettings settings = UISettings.getInstanceOrNull();
    if (settings != null && settings.getCompactTreeIndents()) return Control.Painter.COMPACT;
    if (is("ide.tree.painter.classic.compact")) return Control.Painter.COMPACT;
    if (is("ide.tree.painter.compact.default")) return CompactPainter.DEFAULT;
    return Control.Painter.DEFAULT;
  }

  @Nullable
  private static Color getBackground(@NotNull JTree tree, @NotNull TreePath path, int row, boolean selected) {
    // to be consistent with com.intellij.ui.components.WideSelectionListUI#getBackground
    if (selected) {
      return RenderingUtil.getSelectionBackground(tree);
    }
    if (row == TreeHoverListener.getHoveredRow(tree)) {
      Color background = RenderingUtil.getHoverBackground(tree);
      if (background != null) return background;
    }
    Object node = TreeUtil.getLastUserObject(path);
    if (node instanceof ColoredItem) {
      Color background = ((ColoredItem)node).getColor();
      if (background != null) return background;
    }
    if (node instanceof BackgroundSupplier) {
      BackgroundSupplier supplier = (BackgroundSupplier)node;
      Color background = supplier.getElementBackground(row);
      if (background != null) return background;
    }
    if (tree instanceof TreePathBackgroundSupplier) {
      TreePathBackgroundSupplier supplier = (TreePathBackgroundSupplier)tree;
      Color background = supplier.getPathBackground(path, row);
      if (background != null) return background;
    }
    return null;
  }

  @ApiStatus.Internal
  public static void setBackground(@NotNull JTree tree, @NotNull Component component, int row) {
    TreePath path = tree.getPathForRow(row);
    Color background = path == null ? null : getBackground(tree, path, row, tree.isRowSelected(row));
    setBackground(tree, component, background, true);
  }

  private static void setBackground(@NotNull JTree tree, @NotNull Component component, @Nullable Color background, boolean opaque) {
    if (component instanceof JComponent) {
      ((JComponent)component).setOpaque(opaque);
    }
    if (background != null) {
      component.setBackground(background);
    }
    else if (component.isOpaque()) {
      component.setBackground(RenderingUtil.getBackground(tree));
    }
  }

  private static boolean isSuspiciousRenderer(Component component) {
    if (component instanceof JComponent) {
      Method method = getMethod(component.getClass(), "validate");
      Class<?> type = method == null ? null : method.getDeclaringClass();
      return Component.class.equals(type) || Container.class.equals(type);
    }
    return true;
  }

  private static boolean isLeadSelectionNeeded(@NotNull JTree tree, int row) {
    return 1 < tree.getSelectionCount() && tree.isRowSelected(row - 1) && tree.isRowSelected(row + 1);
  }

  private static boolean isLargeModelAllowed(@Nullable JTree tree) {
    return is("ide.tree.large.model.allowed") || UIUtil.isClientPropertyTrue(tree, LARGE_MODEL_ALLOWED);
  }

  private static boolean isAutoExpandAllowed(@NotNull JTree tree) {
    Boolean allowed = UIUtil.getClientProperty(tree, AUTO_EXPAND_ALLOWED);
    return allowed != null ? allowed : tree.isShowing();
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(JComponent component) {
    assert component instanceof JTree;
    return new DefaultTreeUI();
  }

  // non static

  private final Control control = new DefaultControl();
  private final AtomicBoolean painting = new AtomicBoolean();
  private final DispatchThreadValidator validator = new DispatchThreadValidator();

  @Nullable
  private JTree getTree() {
    return super.tree; // TODO: tree ???
  }

  @Nullable
  private Component getRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean focused) {
    TreeCellRenderer renderer = value instanceof LoadingNode ? LoadingNodeRenderer.SHARED : super.currentCellRenderer;
    if (renderer == null) return null;
    Component component = renderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focused);
    if (component == null) return null;

    CellRendererPane pane = rendererPane; // TODO: rendererPane ???
    if (pane != null && pane != component.getParent()) pane.add(component);

    if (LOG.isDebugEnabled()) {
      Class<? extends TreeCellRenderer> type = renderer.getClass();
      if (!SUSPICIOUS.contains(type) && isSuspiciousRenderer(component) && SUSPICIOUS.add(type)) {
        LOG.debug("suspicious renderer " + type);
      }
    }
    return component;
  }

  private boolean isLeaf(@Nullable Object value) {
    return value == null || super.treeModel.isLeaf(value); // TODO: treeModel ???
  }

  private boolean isValid(@Nullable JTree tree) {
    if (!validator.isValidThread()) return false;
    if (tree != null && tree == getTree()) return true;
    LOG.warn(new IllegalStateException(tree != null ? "unexpected tree" : "undefined tree"));
    return false;
  }

  private void repaintPath(@Nullable TreePath path) {
    Rectangle bounds = getPathBounds(getTree(), path);
    if (bounds != null) tree.repaint(0, bounds.y, tree.getWidth(), bounds.height);
  }

  private void removeCachedRenderers() {
    CellRendererPane pane = painting.get() ? null : rendererPane;
    if (pane != null) pane.removeAll();
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
      painting.set(true);
      Rectangle paintBounds = g.getClipBounds();
      Insets insets = tree.getInsets();
      TreePath path = cache.getPathClosestTo(0, paintBounds.y - insets.top);
      int row = cache.getRowForPath(path);
      if (row >= 0) {
        boolean dark = isDark(JBUI.CurrentTheme.Tree.BACKGROUND);
        Control.Painter painter = getPainter(tree);
        Rectangle buffer = new Rectangle();
        RenderingHelper helper = new RenderingHelper(tree);
        int maxPaintY = paintBounds.y + paintBounds.height;
        while (path != null) {
          Rectangle bounds = cache.getBounds(path, buffer);
          if (bounds == null) break; // something goes wrong
          bounds.y += insets.top;

          int depth = TreeUtil.getNodeDepth(tree, path);
          boolean leaf = isLeaf(path.getLastPathComponent());
          boolean expanded = !leaf && cache.getExpandedState(path);
          boolean selected = tree.isRowSelected(row);
          boolean focused = RenderingUtil.isFocused(tree);
          boolean lead = focused && row == getLeadSelectionRow();
          boolean selectedControl = selected && focused;

          Color background = getBackground(tree, path, row, selected);
          if (background != null) {
            g.setColor(background);
            if (g instanceof Graphics2D && is("ide.experimental.ui.tree.selection") && (selected || row == TreeHoverListener.getHoveredRow(tree))) {
              int borderOffset = JBUI.scale(12);
              int rendererOffset = painter.getRendererOffset(control, depth, leaf);
              int controlOffset = painter.getControlOffset(control, depth, leaf);
              int left = Math.min(helper.getX() + borderOffset, controlOffset < 0 ? rendererOffset : controlOffset);
              int right = Math.max(helper.getX() + helper.getWidth() - borderOffset, rendererOffset + bounds.width + JBUI.scale(4));
              int[] rows = tree.getSelectionRows();
              boolean shouldPaintTop = false;
              boolean shouldPaintBottom = false;
              if (rows != null && rows.length > 1) {
                for (int selectedRow : rows) {
                  int delta = selectedRow - row;
                  if (delta == 1) shouldPaintBottom = true;
                  if (delta == -1) shouldPaintTop = true;
                  if (shouldPaintTop && shouldPaintBottom) break;
                }
              }

              if (shouldPaintTop && shouldPaintBottom) {
                g.fillRect(left, bounds.y, right - left, bounds.height);
              } else {
                int arc = JBUI.scale(8);
                FILL.paint((Graphics2D)g, left, bounds.y, right - left, bounds.height, arc);
                if (shouldPaintTop) {
                  g.fillRect(left, bounds.y, right - left, arc);
                }
                if (shouldPaintBottom) {
                  g.fillRect(left, bounds.y + bounds.height - arc, right - left, arc);
                }
              }
              //if (lead) {
              //  g.setColor(Color.lightGray);
              //  ((Graphics2D)g).setStroke(new BasicStroke(1.0f,
              //                                            BasicStroke.CAP_BUTT,
              //                                            BasicStroke.JOIN_MITER,
              //                                            2.0f, new float[]{2.0f}, 0.0f));
              //  g.drawRoundRect(left + 1, bounds.y, right - left - 2, bounds.height-1, JBUI.scale(8), JBUI.scale(8));
              //}
            }
            else {
              g.fillRect(helper.getX(), bounds.y, helper.getWidth(), bounds.height);
            }
            if (selectedControl && !dark && !isDark(background)) selectedControl = false;
          }
          int offset = painter.getRendererOffset(control, depth, leaf);
          painter.paint(tree, g, insets.left, bounds.y, offset, bounds.height, control, depth, leaf, expanded, selectedControl);
          // TODO: editingComponent, editingRow ???
          if (editingComponent == null || editingRow != row) {
            int width = helper.getX() + helper.getWidth() - insets.left - offset;
            if (width > 0) {
              Object value = path.getLastPathComponent();
              Component component = getRenderer(tree, value, selected, expanded, leaf, row, lead);
              if (component != null) {
                width -= helper.getRightMargin(); // shrink a long node according to the right margin
                if (width < bounds.width && helper.isRendererShrinkingDisabled(row)) {
                  width = bounds.width; // disable shrinking a long nodes
                }
                if (width > 0) {
                  setBackground(tree, component, background, false);
                  rendererPane.paintComponent(g, component, tree, insets.left + offset, bounds.y, width, bounds.height, true);
                }
              }
            }
            if (!isMac && lead && g instanceof Graphics2D) {
              if (!selected) {
                g.setColor(getBackground(tree, path, row, true));
                DRAW.paint((Graphics2D)g, helper.getX(), bounds.y, helper.getWidth(), bounds.height, 0);
              }
              else if (isLeadSelectionNeeded(tree, row)) {
                g.setColor(RenderingUtil.getBackground(tree));
                DRAW.paint((Graphics2D)g, helper.getX() + 1, bounds.y + 1, helper.getWidth() - 2, bounds.height - 2, 0);
              }
            }
            JTree.DropLocation dropLocation = tree.getDropLocation();
            if (dropLocation != null && g instanceof Graphics2D && path.equals(dropLocation.getPath())) {
              // paint a dragged tree path in accordance to Highlighters.RectangleHighlighter
              g.setColor(JBUI.CurrentTheme.DragAndDrop.ROW_BACKGROUND);
              FILL.paint((Graphics2D)g, helper.getX(), bounds.y, helper.getWidth(), bounds.height, 0);
              g.setColor(JBUI.CurrentTheme.DragAndDrop.BORDER_COLOR);
              DRAW.paint((Graphics2D)g, helper.getX(), bounds.y, helper.getWidth(), bounds.height, 0);
            }
          }
          if ((bounds.y + bounds.height) >= maxPaintY) break;
          path = cache.getPathForRow(++row);
        }
      }
    }
    finally {
      g.dispose();
      painting.set(false);
      removeCachedRenderers();
    }
  }

  // BasicTreeUI

  @Override
  protected void installDefaults() {
    super.installDefaults();
    if (!isLargeModelAllowed(getTree())) largeModel = false;
    JTree tree = getTree();
    if (tree != null) {
      LookAndFeel.installBorder(tree, "Tree.border");
      if (tree.isForegroundSet()) tree.setForeground(null);
      if (UIManager.get("Tree.showsRootHandles") == null) {
        LookAndFeel.installProperty(tree, JTree.SHOWS_ROOT_HANDLES_PROPERTY, Boolean.TRUE);
      }
    }
  }

  @Override
  protected void installKeyboardActions() {
    super.installKeyboardActions();
    TreeAction.installTo(tree.getActionMap());
    TreeAction.installTo(tree.getInputMap(JComponent.WHEN_FOCUSED));
  }

  @Override
  protected boolean isToggleEvent(MouseEvent event) {
    if (!super.isToggleEvent(event)) return false;
    JTree tree = getTree();
    return tree != null && isExpandPreferable(tree, tree.getSelectionPath());
  }

  @Override
  protected boolean isLocationInExpandControl(TreePath path, int mouseX, int mouseY) {
    JTree tree = getTree();
    if (tree == null) return false;
    Rectangle bounds = getPathBounds(tree, path);
    if (bounds == null) return false;
    bounds.x = getPainter(tree).getControlOffset(control, TreeUtil.getNodeDepth(tree, path), isLeaf(path.getLastPathComponent()));
    if (bounds.x < 0) return false; // does not paint an icon to expand or collapse path
    Insets insets = tree.getInsets();
    bounds.x += insets.left;
    bounds.width = control.getWidth();
    int height = 2 + control.getHeight();
    if (height < bounds.height) {
      bounds.y += (bounds.height - height) / 2;
      bounds.height = height;
    }
    return bounds.contains(mouseX, mouseY);
  }

  @Override
  protected void configureLayoutCache() {
    super.configureLayoutCache();
    JTree tree = getTree();
    if (tree != null && null == ReflectionUtil.getField(BasicTreeUI.class, this, null, "componentListener")) {
      ComponentListener listener = createComponentListener();
      ComponentAdapter adapter = new ComponentAdapter() {
        @Override
        public void componentMoved(ComponentEvent event) {
          AbstractLayoutCache cache = treeState; // TODO: treeState ???
          if (cache != null && tree.isLargeModel() && is("ide.tree.experimental.preferred.width")) {
            listener.componentMoved(event);
          }
        }
      };
      ReflectionUtil.setField(BasicTreeUI.class, this, null, "componentListener", adapter);
      tree.addComponentListener(adapter);
    }
  }

  @Override
  protected void updateCachedPreferredSize() {
    JTree tree = getTree();
    AbstractLayoutCache cache = treeState; // TODO: treeState ???
    if (tree != null && isValid(tree) && cache != null && tree.isLargeModel() && is("ide.tree.experimental.preferred.width")) {
      Rectangle paintBounds = tree.getVisibleRect();
      if (!paintBounds.isEmpty()) {
        JScrollPane pane = UIUtil.getParentOfType(JScrollPane.class, tree);
        if (pane != null) {
          JScrollBar bar = pane.getHorizontalScrollBar();
          if (bar != null && bar.isOpaque() && bar.isVisible()) {
            paintBounds.height += bar.getPreferredSize().height;
          }
        }
        Insets insets = tree.getInsets();
        TreePath path = cache.getPathClosestTo(0, paintBounds.y - insets.top);
        int row = cache.getRowForPath(path);
        if (row >= 0) {
          Rectangle buffer = new Rectangle();
          int maxPaintX = paintBounds.x + paintBounds.width;
          int maxPaintY = paintBounds.y + paintBounds.height;
          int width = 0;
          while (path != null) {
            Rectangle bounds = cache.getBounds(path, buffer);
            if (bounds == null) continue; // something goes wrong
            width = Math.max(width, bounds.x + bounds.width);
            if ((bounds.y + bounds.height) >= maxPaintY) break;
            path = cache.getPathForRow(++row);
          }
          width += insets.left + insets.right;
          if (width < maxPaintX) {
            if (!is("ide.tree.prefer.to.shrink.width.on.scroll")) {
              width = maxPaintX;
            }
            else if (paintBounds.width < width || !is("ide.tree.prefer.aggressive.scrolling.to.the.left")) {
              int margin = intValue("ide.tree.preferable.right.margin", 25);
              if (margin > 0) width = Math.min(width + paintBounds.width * margin / 100, maxPaintX);
            }
          }
          preferredSize.width = width;
          preferredSize.height = insets.top + insets.bottom + cache.getPreferredHeight();
          validCachedPreferredSize = true;
          return;
        }
      }
    }
    super.updateCachedPreferredSize();
  }

  @Override
  protected int getRowX(int row, int depth) {
    JTree tree = getTree();
    if (tree == null) return 0;
    TreePath path = getPathForRow(tree, row);
    if (path == null) return 0;
    return getPainter(tree).getRendererOffset(control, TreeUtil.getNodeDepth(tree, path), isLeaf(path.getLastPathComponent()));
  }

  @Override
  protected void setRootVisible(boolean newValue) {
    if (treeModel instanceof AsyncTreeModel) {
      // this method must be called on EDT to be consistent with ATM,
      // because it modifies a list of visible nodes in the layout cache
      UIUtil.invokeLaterIfNeeded(() -> super.setRootVisible(newValue));
    }
    else {
      super.setRootVisible(newValue);
    }
  }

  @Override
  protected void setLargeModel(boolean large) {
    super.setLargeModel(large && isLargeModelAllowed(getTree()));
  }

  @Override
  protected void setModel(TreeModel model) {
    if (!isLargeModelAllowed(getTree())) largeModel = false;
    super.setModel(model);
  }

  @Override
  protected void updateSize() {
    if (getTree() != null) super.updateSize();
  }

  @Override
  protected void completeEditing() {
    if (getTree() != null) super.completeEditing();
  }

  @Override
  protected AbstractLayoutCache.NodeDimensions createNodeDimensions() {
    return new AbstractLayoutCache.NodeDimensions() {
      @Override
      public Rectangle getNodeDimensions(Object value, int row, int depth, boolean expanded, Rectangle bounds) {
        JTree tree = getTree();
        if (tree == null) return null;

        boolean leaf = isLeaf(value);
        Dimension size = null;
        // TODO: editingComponent, editingRow ???
        if (editingComponent != null && editingRow == row) {
          size = editingComponent.getPreferredSize();
        }
        else {
          boolean selected = tree.isRowSelected(row);
          Component component = getRenderer(tree, value, selected, expanded, leaf, row, false);
          if (component != null) {
            component.validate();
            size = component.getPreferredSize();
            removeCachedRenderers();
          }
        }
        if (size == null) return null;

        int x = getPainter(tree).getRendererOffset(control, depth + TreeUtil.getDepthOffset(tree), leaf);
        int height = getRowHeight();
        if (height <= 0) height = size.height;
        if (bounds == null) return new Rectangle(x, 0, size.width, height);

        bounds.x = x;
        bounds.y = 0;
        bounds.width = size.width;
        bounds.height = height;
        return bounds;
      }
    };
  }

  @Override
  protected AbstractLayoutCache createLayoutCache() {
    if (isLargeModel() && getRowHeight() > 0) {
      return new FixedHeightLayoutCache();
    }
    return new VariableHeightLayoutCache() {
      @Override
      public void setExpandedState(TreePath path, boolean isExpanded) {
        int oldRowCount = getRowCount();
        super.setExpandedState(path, isExpanded);
        if (isExpanded) onSingleChildInserted(path, oldRowCount);
      }

      @Override
      public void treeNodesInserted(TreeModelEvent event) {
        int oldRowCount = getRowCount();
        super.treeNodesInserted(event);
        onSingleChildInserted(event.getTreePath(), oldRowCount);
      }

      private void onSingleChildInserted(TreePath path, int oldRowCount) {
        if (path == null || oldRowCount + 1 != getRowCount()) return;
        JTree tree = getTree();
        if (tree == null || !isAutoExpandAllowed(tree) || !tree.isVisible(path)) return;
        TreeModel model = tree.getModel();
        if (model instanceof AsyncTreeModel && 1 == model.getChildCount(path.getLastPathComponent())) {
          int pathCount = 1 + path.getPathCount();
          for (int i = 0; i <= oldRowCount; i++) {
            TreePath row = getPathForRow(i);
            if (row != null && pathCount == row.getPathCount() && path.equals(row.getParentPath())) {
              ((AsyncTreeModel)model).onValidThread(() -> tree.expandPath(row));
              return; // this code is intended to auto-expand a single child node
            }
          }
        }
      }
    };
  }

  @Override
  protected MouseListener createMouseListener() {
    return new MouseEventAdapter<>(super.createMouseListener()) {
      @Override
      public void mouseDragged(MouseEvent event) {
        Object property = UIUtil.getClientProperty(event.getSource(), "DnD Source"); // DnDManagerImpl.SOURCE_KEY
        if (property == null) super.mouseDragged(event); // use Swing-based DnD only if custom DnD is not set
      }

      @NotNull
      @Override
      protected MouseEvent convert(@NotNull MouseEvent event) {
        JTree tree = getTree();
        if (tree != null && tree == event.getSource() && tree.isEnabled()) {
          if (!event.isConsumed() && SwingUtilities.isLeftMouseButton(event)) {
            int x = event.getX();
            int y = event.getY();
            TreePath path = getClosestPathForLocation(tree, x, y);
            if (path != null && !isLocationInExpandControl(path, x, y)) {
              Rectangle bounds = getPathBounds(tree, path);
              if (bounds != null && bounds.y <= y && y <= (bounds.y + bounds.height)) {
                x = Math.max(bounds.x, Math.min(x, bounds.x + bounds.width - 1));
                if (x != event.getX()) event = convert(event, tree, x, y);
              }
            }
          }
        }
        return event;
      }
    };
  }

  @Override
  protected PropertyChangeListener createPropertyChangeListener() {
    PropertyChangeListener parent = super.createPropertyChangeListener();
    return new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent event) {
        String name = event.getPropertyName();
        if (JTree.ANCHOR_SELECTION_PATH_PROPERTY.equals(name)) return;
        if (JTree.LEAD_SELECTION_PATH_PROPERTY.equals(name)) {
          updateLeadSelectionRow();
          repaintPath((TreePath)event.getOldValue());
          repaintPath((TreePath)event.getNewValue());
        }
        else if (parent != null) {
          parent.propertyChange(event);
        }
      }
    };
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
