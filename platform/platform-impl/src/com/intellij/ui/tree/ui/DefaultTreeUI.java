// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ColoredItem;
import com.intellij.openapi.util.Key;
import com.intellij.ui.*;
import com.intellij.ui.hover.TreeHoverListener;
import com.intellij.ui.render.RenderingHelper;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.ui.tree.TreePathBackgroundSupplier;
import com.intellij.ui.treeStructure.BgtAwareTreeModel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.TreeUiBulkExpandCollapseSupport;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MouseEventAdapter;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import kotlin.Unit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

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
public class DefaultTreeUI extends BasicTreeUI implements TreeUiBulkExpandCollapseSupport, CustomBoundsTreeUI {
  @ApiStatus.Internal
  public static final Key<Boolean> LARGE_MODEL_ALLOWED = Key.create("allows to use large model (only for synchronous tree models)");
  public static final Key<Boolean> AUTO_EXPAND_ALLOWED = Key.create("allows to expand a single child node automatically in tests");
  public static final Key<Function<Object, Boolean>> AUTO_EXPAND_FILTER =
    Key.create("allows to filter single child nodes which should not be auto-expanded");

  @ApiStatus.Internal
  public static final int HORIZONTAL_SELECTION_OFFSET = 12;

  private static final Logger LOG = Logger.getInstance(DefaultTreeUI.class);
  private static final Collection<Class<?>> SUSPICIOUS = createWeakSet();

  private static @NotNull Control.Painter getPainter(@NotNull JTree tree) {
    Control.Painter painter = ClientProperty.get(tree, Control.Painter.KEY);
    if (painter != null) {
      return painter;
    }
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

  @ApiStatus.Internal
  public static @Nullable Color getBackground(@NotNull JTree tree, @NotNull TreePath path, int row, boolean selected) {
    Object node = TreeUtil.getLastUserObject(path);
    // to be consistent with com.intellij.ui.components.WideSelectionListUI#getBackground
    if (selected) {
      if (node instanceof BackgroundSupplier supplier) {
        Color background = supplier.getSelectedElementBackground(row);
        if (background != null) return background;
      }
      return RenderingUtil.getSelectionBackground(tree);
    }
    if (row == TreeHoverListener.getHoveredRow(tree)) {
      Color background = RenderingUtil.getHoverBackground(tree);
      if (background != null) return background;
    }
    if (node instanceof ColoredItem) {
      Color background = ((ColoredItem)node).getColor();
      if (background != null) return background;
    }
    if (node instanceof BackgroundSupplier supplier) {
      Color background = supplier.getElementBackground(row);
      if (background != null) return background;
    }
    if (tree instanceof TreePathBackgroundSupplier supplier) {
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

  @ApiStatus.Internal
  public static boolean isSeparator(@Nullable TreePath path) {
    return path != null && isSeparator(path.getLastPathComponent());
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
    return is("ide.tree.large.model.allowed") || ClientProperty.isTrue(tree, LARGE_MODEL_ALLOWED);
  }

  private static boolean isAutoExpandAllowed(@NotNull JTree tree) {
    Boolean allowed = ClientProperty.get(tree, AUTO_EXPAND_ALLOWED);
    return allowed != null ? allowed : tree.isShowing();
  }

  private static boolean isAutoExpandAllowed(@NotNull JTree tree, @NotNull Object node) {
    Function<Object, Boolean> filter = ClientProperty.get(tree, AUTO_EXPAND_FILTER);
    if (filter != null) {
      return !filter.apply(node);
    }
    else if (node instanceof AbstractTreeNode<?> treeNode) {
      return treeNode.isAutoExpandAllowed();
    }
    else {
      return true;
    }
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(JComponent component) {
    assert component instanceof JTree;
    return new DefaultTreeUI();
  }

  // non static

  private final Control myDefaultControl = new DefaultControl();

  private @NotNull Control getControl(@NotNull JComponent c, @NotNull TreePath path) {
    Function<@NotNull TreePath, @Nullable Control> controlResolver = ClientProperty.get(c, Control.CUSTOM_CONTROL);
    Control resolvedControl = controlResolver != null ? controlResolver.apply(path) : myDefaultControl;

    return ObjectUtils.chooseNotNull(resolvedControl, myDefaultControl);
  }

  private final AtomicBoolean painting = new AtomicBoolean();
  private final DispatchThreadValidator validator = new DispatchThreadValidator();

  private @Nullable JTree getTree() {
    return super.tree; // TODO: tree ???
  }

  private @Nullable Component getRenderer(JTree tree, Object value, boolean selected, boolean expanded,
                                          boolean leaf, int row, boolean focused) {
    if (isSeparator(value) && value instanceof Component) {
      return (Component)value;
    }

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
    return value == null || isSeparator(value) || super.treeModel.isLeaf(value); // TODO: treeModel ???
  }

  private static boolean isSeparator(@Nullable Object value) {
    return value instanceof SeparatorWithText;
  }

  private boolean isValid(@Nullable JTree tree) {
    if (!validator.isValidThread()) {
      LOG.error("TreeUI should be accessed only from EDT");
      return false;
    }
    if (tree != null && tree == getTree()) return true;
    LOG.warn(new IllegalStateException(tree != null ? "unexpected tree" : "undefined tree"));
    return false;
  }

  private void repaintPath(@Nullable TreePath path) {
    var tree = getTree();
    if (tree != null) TreeUtil.repaintPath(tree, path);
  }

  private void removeCachedRenderers() {
    CellRendererPane pane = painting.get() ? null : rendererPane;
    if (pane != null) pane.removeAll();
  }

  @Override
  public @Nullable Rectangle getActualPathBounds(@NotNull JTree tree, @NotNull TreePath path) {
    RenderingHelper helper = new RenderingHelper(tree);
    Control.Painter painter = getPainter(tree);

    return getActualPathBounds(tree, path, helper, painter);
  }

  private Rectangle getActualPathBounds(
    @NotNull JTree tree, @NotNull TreePath path,
    RenderingHelper helper, Control.Painter painter
  ) {
    AbstractLayoutCache cache = treeState;

    Insets insets = tree.getInsets();

    Rectangle bounds = new Rectangle();
    cache.getBounds(path, bounds);
    bounds.y += insets.top;

    Object value = path.getLastPathComponent();
    int depth = TreeUtil.getNodeDepth(tree, path);
    boolean leaf = isLeaf(value);
    Control control = getControl(tree, path);
    int offset = painter.getRendererOffset(control, depth, leaf);

    int width = helper.getX() + helper.getWidth() - insets.left - offset;
    int row = cache.getRowForPath(path);

    width -= helper.getRightMargin(); // shrink a long node according to the right margin
    if (width < bounds.width && helper.isRendererShrinkingDisabled(row)) {
      width = bounds.width; // disable shrinking a long nodes
    }
    return new Rectangle(insets.left + offset, bounds.y, width, bounds.height);
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
          Object value = path.getLastPathComponent();
          boolean leaf = isLeaf(value);
          boolean expanded = !leaf && cache.getExpandedState(path);
          boolean selected = tree.isRowSelected(row);
          boolean focused = RenderingUtil.isFocused(tree);
          boolean lead = focused && row == getLeadSelectionRow();
          boolean selectedControl = selected && focused;

          Color background = isSeparator(value) ? null : getBackground(tree, path, row, selected);
          if (background != null) {
            g.setColor(background);
            if (g instanceof Graphics2D &&
                ExperimentalUI.isNewUI() &&
                is("ide.experimental.ui.tree.selection") &&
                !(tree instanceof PlainSelectionTree) &&
                (selected || row == TreeHoverListener.getHoveredRow(tree))) {
              int borderOffset = JBUI.scale(HORIZONTAL_SELECTION_OFFSET);
              Control control = getControl(c, path);
              int rendererOffset = painter.getRendererOffset(control, depth, leaf);
              int controlOffset = painter.getControlOffset(control, depth, leaf);
              int left = Math.min(helper.getX() + borderOffset, insets.left + (controlOffset < 0 ? rendererOffset : controlOffset));
              int treeRight = helper.getX() + helper.getWidth() - borderOffset;
              int right = treeRight;
              if (helper.isShrinkingSelectionDisabled(row)) {
                int rendererRight = insets.left + rendererOffset + bounds.width + JBUI.scale(4);
                right = Math.max(treeRight, rendererRight);
              }
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
              }
              else {
                int arc = JBUI.CurrentTheme.Tree.ARC.get();
                FILL.paint((Graphics2D)g, left, bounds.y, right - left, bounds.height, arc);
                if (shouldPaintTop) {
                  g.fillRect(left, bounds.y, right - left, arc);
                }
                if (shouldPaintBottom) {
                  g.fillRect(left, bounds.y + bounds.height - arc, right - left, arc);
                }
              }
            }
            else {
              g.fillRect(helper.getX(), bounds.y, helper.getWidth(), bounds.height);
            }
            if (selectedControl && !dark && !isDark(background)) {
              selectedControl = false;
            }
          }

          Control control = getControl(c, path);

          int offset = painter.getRendererOffset(control, depth, leaf);
          painter.paint(tree, g, insets.left, bounds.y, offset, bounds.height, control, depth, leaf, expanded, selectedControl);
          // TODO: editingComponent, editingRow ???
          if (editingComponent == null || editingRow != row) {
            int width = helper.getX() + helper.getWidth() - insets.left - offset;
            if (width > 0) {
              Component component = getRenderer(tree, value, selected, expanded, leaf, row, lead);

              if (isSeparator(component)) {
                int x;
                if (ExperimentalUI.isNewUI()) {
                  Insets separatorInsets = JBUI.CurrentTheme.Popup.separatorInsets();
                  x = paintBounds.x + separatorInsets.left;
                  width = paintBounds.width - separatorInsets.left - separatorInsets.right;
                }
                else {
                  int separatorWidthGap = JBUI.scale(SeparatorWithText.DEFAULT_H_GAP);
                  x = paintBounds.x + separatorWidthGap;
                  width = paintBounds.width - 2 * separatorWidthGap;
                }
                if (width > 0) {
                  rendererPane.paintComponent(g, component, tree, x, bounds.y, width, bounds.height, true);
                }
              }
              else if (component != null) {
                Rectangle compBounds = getActualPathBounds(tree, path, helper, painter);
                if (compBounds.width > 0) {
                  setBackground(tree, component, background, false);
                  rendererPane.paintComponent(g, component, tree, compBounds.x, compBounds.y, compBounds.width, compBounds.height, true);
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
    return tree != null
           // BasicTreeUI uses clickCount % toggleClickCount == 0, which works terrible for single-click, so we double-check here:
           && tree.getToggleClickCount() == event.getClickCount()
           && isExpandPreferable(tree, tree.getSelectionPath());
  }

  @Override
  protected void toggleExpandState(TreePath path) {
    if (!tree.isExpanded(path) && tree instanceof Tree) {
      ((Tree)tree).startMeasuringExpandDuration(path);
    }
    super.toggleExpandState(path);
  }

  @ApiStatus.Internal
  public boolean isLocationInExpandControl(@NotNull Point location) {
    var tree = getTree();
    if (tree == null) return false;
    var path = getClosestPathForLocation(tree, location.x, location.y);
    if (path == null) return false;
    return isLocationInExpandControl(path, location.x, location.y);
  }

  @Override
  protected boolean isLocationInExpandControl(TreePath path, int mouseX, int mouseY) {
    JTree tree = getTree();
    if (tree == null) return false;
    Rectangle bounds = getPathBounds(tree, path);
    if (bounds == null) return false;
    Control control = getControl(tree, path);
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
          if (cache != null && is("ide.tree.experimental.preferred.width", true)) {
            listener.componentMoved(event);
          }
        }
      };
      ReflectionUtil.setField(BasicTreeUI.class, this, null, "componentListener", adapter);
      tree.addComponentListener(adapter);
    }
  }

  @Override
  public Dimension getPreferredSize(JComponent c, boolean checkConsistency) {
    if (treeState instanceof DefaultTreeLayoutCache cache) {
      // If the cache decided to invalidate sizes, we need to propagate it here,
      // so super() will query the cache again.
      if (!cache.isCachedSizeValid) {
        validCachedPreferredSize = false;
      }
      var result = super.getPreferredSize(c, checkConsistency);
      cache.isCachedSizeValid = true;
      return result;
    }
    else {
      return super.getPreferredSize(c, checkConsistency);
    }
  }

  @Override
  protected void updateCachedPreferredSize() {
    JTree tree = getTree();
    AbstractLayoutCache cache = treeState;
    if (tree == null || !isValid(tree) || cache == null) {
      preferredSize.width = 0;
      preferredSize.height = 0;
      validCachedPreferredSize = true;
    }
    else if (is("ide.tree.experimental.preferred.width", true)) {
      Rectangle paintBounds = tree.getVisibleRect();
      Insets insets = tree.getInsets();
      int visibleRowCount = tree.getVisibleRowCount();
      if (paintBounds.isEmpty()) {
        // no valid bounds yet, fall back to something that will work at least, mimic DefaultTreeUI
        paintBounds.width = 1 + insets.left + insets.right;
        paintBounds.height = tree.getRowHeight() * visibleRowCount + insets.top + insets.bottom;
      }
      JScrollPane pane = UIUtil.getParentOfType(JScrollPane.class, tree);
      if (pane != null) {
        JScrollBar bar = pane.getHorizontalScrollBar();
        if (bar != null && bar.isOpaque() && bar.isVisible()) {
          paintBounds.height += bar.getPreferredSize().height;
        }
      }
      TreePath path = cache.getPathClosestTo(0, paintBounds.y - insets.top);
      int width = 0;
      int row = cache.getRowForPath(path);
      if (row >= 0) {
        Rectangle buffer = new Rectangle();
        int maxPaintX = paintBounds.x + paintBounds.width;
        int maxPaintY = paintBounds.y + paintBounds.height;
        int rowsUsed = 0;
        for (; path != null; path = cache.getPathForRow(++row)) {
          Rectangle bounds = cache.getBounds(path, buffer);
          if (bounds == null) {
            LOG.warn("The bounds for the row " + row + " of the tree " + tree + " with model " + treeModel +
                     " are null, looks like a bug in " + cache);
            continue;
          }
          width = Math.max(width, bounds.x + bounds.width);
          ++rowsUsed;
          if (
            (bounds.y + bounds.height) >= maxPaintY &&
            (visibleRowCount <= 0 || rowsUsed >= visibleRowCount)
          ) {
            break;
          }
        }
        width += insets.left + insets.right;
        if (width < maxPaintX) {
          if (!is("ide.tree.prefer.to.shrink.width.on.scroll")) {
            width = maxPaintX;
          }
          else if (paintBounds.width < width || !is("ide.tree.prefer.aggressive.scrolling.to.the.left")) {
            int margin = intValue("ide.tree.preferable.right.margin", 25);
            if (margin > 0) {
              width = Math.min(width + paintBounds.width * margin / 100, maxPaintX);
            }
          }
        }
      }
      else { // row == -1, the tree is empty
        width = insets.left + insets.right;
      }
      preferredSize.width = width;
      preferredSize.height = insets.top + insets.bottom + cache.getPreferredHeight();
      validCachedPreferredSize = true;
    }
    else {
      super.updateCachedPreferredSize();
    }
  }

  @Override
  protected int getRowX(int row, int depth) {
    JTree tree = getTree();
    if (tree == null) {
      return 0;
    }
    TreePath path = getPathForRow(tree, row);
    if (path == null) {
      return 0;
    }
    Control control = getControl(tree, path);
    return getPainter(tree).getRendererOffset(control, TreeUtil.getNodeDepth(tree, path), isLeaf(path.getLastPathComponent()));
  }

  @Override
  protected void setRootVisible(boolean newValue) {
    if (treeModel instanceof BgtAwareTreeModel) {
      // this method must be called on EDT to be consistent with ATM,
      // because it modifies a list of visible nodes in the layout cache
      EdtInvocationManager.invokeLaterIfNeeded(() -> super.setRootVisible(newValue));
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
    if (!isLargeModelAllowed(getTree())) {
      largeModel = false;
    }
    super.setModel(model);
  }

  @Override
  protected void updateSize() {
    if (getTree() != null) {
      super.updateSize();
    }
  }

  @Override
  protected void completeEditing() {
    if (getTree() != null) {
      super.completeEditing();
    }
  }

  @Override
  protected AbstractLayoutCache.NodeDimensions createNodeDimensions() {
    return new AbstractLayoutCache.NodeDimensions() {
      @Override
      public Rectangle getNodeDimensions(Object value, int row, int depth, boolean expanded, Rectangle bounds) {
        JTree tree = getTree();
        if (tree == null) {
          return null;
        }

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

        if (size == null) {
          return null;
        }

        int x = getPainter(tree).getRendererOffset(myDefaultControl, depth + TreeUtil.getDepthOffset(tree), leaf);
        int height = getRowHeight();
        if (height <= 0) {
          height = size.height;
        }
        if (bounds == null) {
          return new Rectangle(x, 0, size.width, height);
        }

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
    if (is("ide.tree.experimental.layout.cache", true)) {
      return new DefaultTreeLayoutCache(path -> {
        handleAutoExpand(path);
        return Unit.INSTANCE;
      });
    }

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
        if (!shouldAutoExpand(tree, path)) return;
        TreeModel model = tree.getModel();
        if (model instanceof BgtAwareTreeModel && 1 == model.getChildCount(path.getLastPathComponent())) {
          int pathCount = 1 + path.getPathCount();
          for (int i = 0; i <= oldRowCount; i++) {
            TreePath row = getPathForRow(i);
            if (row != null && pathCount == row.getPathCount() && path.equals(row.getParentPath())) {
              handleAutoExpand(row);
              // this code is intended to auto-expand a single child node
              return;
            }
          }
        }
      }
    };
  }

  private final @NotNull AtomicInteger bulkOperationsInProgress = new AtomicInteger();

  @Override
  public void beginBulkOperation() {
    if (!bulkOperationsSupported()) {
      return;
    }
    bulkOperationsInProgress.incrementAndGet();
  }

  @Override
  public void endBulkOperation() {
    if (!bulkOperationsSupported()) {
      return;
    }
    if (bulkOperationsInProgress.decrementAndGet() == 0) {
      completeEditing();
      var layoutCache = (DefaultTreeLayoutCache)treeState;
      layoutCache.updateExpandedPaths(((Tree)tree).getExpandedPaths());
      updateLeadSelectionRow();
      updateSize();
    }
  }

  @Override
  protected TreeExpansionListener createTreeExpansionListener() {
    return new MyTreeExpansionListener(super.createTreeExpansionListener());
  }

  private class MyTreeExpansionListener implements TreeExpansionListener {
    private final TreeExpansionListener myOriginal;

    MyTreeExpansionListener(TreeExpansionListener original) {
      myOriginal = original;
    }

    @Override
    public void treeExpanded(TreeExpansionEvent event) {
      if (bulkOperationsInProgress.get() == 0) {
        myOriginal.treeExpanded(event);
      }
    }

    @Override
    public void treeCollapsed(TreeExpansionEvent event) {
      if (bulkOperationsInProgress.get() == 0) {
        myOriginal.treeCollapsed(event);
      }
    }
  }

  private boolean bulkOperationsSupported() {
    return tree instanceof Tree && treeState instanceof DefaultTreeLayoutCache;
  }

  private static boolean shouldAutoExpand(JTree tree, TreePath path) {
    return tree != null && isAutoExpandAllowed(tree) && tree.isVisible(path);
  }

  private void handleAutoExpand(@NotNull TreePath row) {
    var tree = getTree();
    if (!shouldAutoExpand(tree, row.getParentPath())) {
      return;
    }
    if (tree.getModel() instanceof BgtAwareTreeModel) {
      Object node = row.getLastPathComponent();
      if (isAutoExpandAllowed(tree, node)) {
        EdtInvocationManager.invokeLaterIfNeeded(() -> tree.expandPath(row));
      }
    }
  }

  @Override
  protected MouseListener createMouseListener() {
    return new MouseEventAdapter<>(super.createMouseListener()) {
      @Override
      public void mouseDragged(MouseEvent event) {
        Object component = event.getSource();
        // DnDManagerImpl.SOURCE_KEY
        Object property = component instanceof Component ? ClientProperty.get((Component)component, "DnD Source") : null;
        if (property == null) {
          // use Swing-based DnD only if custom DnD is not set
          super.mouseDragged(event);
        }
      }

      @Override
      protected @NotNull MouseEvent convert(@NotNull MouseEvent event) {
        JTree tree = getTree();
        if (tree == null || tree != event.getSource() || !tree.isEnabled()) {
          return event;
        }

        if (!event.isConsumed() && SwingUtilities.isLeftMouseButton(event)) {
          int x = event.getX();
          int y = event.getY();
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
    return path != null && isValid(tree) ? super.getPathBounds(tree, path) : null;
  }

  @Override
  public TreePath getPathForRow(JTree tree, int row) {
    return isValid(tree) ? super.getPathForRow(tree, row) : null;
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
    return isValid(tree) ? super.getClosestPathForLocation(tree, x, y) : null;
  }

  @Override
  public boolean isEditing(JTree tree) {
    return isValid(tree) && super.isEditing(tree);
  }

  @Override
  public boolean stopEditing(JTree tree) {
    return isValid(tree) && super.stopEditing(tree);
  }

  @Override
  public void cancelEditing(JTree tree) {
    if (!isValid(tree)) {
      return;
    }
    super.cancelEditing(tree);
  }

  @Override
  public void startEditingAtPath(JTree tree, TreePath path) {
    if (path == null || !isValid(tree)) {
      return;
    }
    super.startEditingAtPath(tree, path);
  }

  @Override
  public TreePath getEditingPath(JTree tree) {
    if (!isValid(tree)) {
      return null;
    }
    return super.getEditingPath(tree);
  }
}
