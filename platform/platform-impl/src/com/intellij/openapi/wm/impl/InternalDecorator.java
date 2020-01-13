// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.ComponentWithMnemonics;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.content.Content;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Map;

/**
 * @author Eugene Belyaev
 * @author Vladimir Kondratyev
 */
public final class InternalDecorator extends JPanel implements Queryable, DataProvider, ComponentWithMnemonics {
  private final ToolWindowImpl toolWindow;
  private final JPanel divider;
  private IdeGlassPane glassPane;

  private Disposable disposable;
  private final MouseAdapter myListener = new MyMouseListener();
  private boolean isDragging;

  /**
   * Catches all event from tool window and modifies decorator's appearance.
   */
  static final String HIDE_ACTIVE_WINDOW_ACTION_ID = "HideActiveWindow";

  // see ToolWindowViewModeAction and ToolWindowMoveAction
  public static final String TOGGLE_DOCK_MODE_ACTION_ID = "ToggleDockMode";
  public static final String TOGGLE_FLOATING_MODE_ACTION_ID = "ToggleFloatingMode";
  public static final String TOGGLE_SIDE_MODE_ACTION_ID = "ToggleSideMode";

  private final ToolWindowHeader header;

  InternalDecorator(@NotNull ToolWindowImpl toolWindow, @NotNull ToolWindowContentUi contentUi) {
    super(new BorderLayout());

    this.toolWindow = toolWindow;
    divider = new JPanel() {
      @NotNull
      @Override
      public Cursor getCursor() {
        WindowInfo info = InternalDecorator.this.toolWindow.getWindowInfo();
        boolean isVerticalCursor = info.getType() == ToolWindowType.DOCKED ? info.getAnchor().isSplitVertically() : info.getAnchor().isHorizontal();
        return isVerticalCursor ? Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR) : Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
      }
    };

    setFocusable(false);
    setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());

    header = new ToolWindowHeader(toolWindow, contentUi, () -> toolWindow.createPopupGroup(true)) {
      @Override
      protected boolean isActive() {
        return toolWindow.isActive();
      }

      @Override
      protected void hideToolWindow() {
        toolWindow.getToolWindowManager().hideToolWindow(toolWindow.getId(), false);
      }
    };

    enableEvents(AWTEvent.COMPONENT_EVENT_MASK);

    installFocusTraversalPolicy(this, new LayoutFocusTraversalPolicy());
    add(header, BorderLayout.NORTH);
    if (SystemInfo.isMac) {
      setBackground(new JBColor(Gray._200, Gray._90));
    }

    setBorder(new InnerPanelBorder(toolWindow));
  }

  @Override
  public String toString() {
    return toolWindow.getId();
  }

  void applyWindowInfo(@NotNull WindowInfo info) {
    // Anchor
    ToolWindowAnchor anchor = info.getAnchor();
    if (info.isSliding()) {
      divider.invalidate();
      if (anchor == ToolWindowAnchor.TOP) {
        add(divider, BorderLayout.SOUTH);
      }
      else if (anchor == ToolWindowAnchor.LEFT) {
        add(divider, BorderLayout.EAST);
      }
      else if (anchor == ToolWindowAnchor.BOTTOM) {
        add(divider, BorderLayout.NORTH);
      }
      else if (anchor == ToolWindowAnchor.RIGHT) {
        add(divider, BorderLayout.WEST);
      }
      divider.setPreferredSize(new Dimension(0, 0));
    }
    else {
      // docked and floating windows don't have divider
      remove(divider);
    }

    // push "apply" request forward
    if (info.isFloating()) {
      FloatingDecorator floatingDecorator = (FloatingDecorator)SwingUtilities.getAncestorOfClass(FloatingDecorator.class, this);
      if (floatingDecorator != null) {
        floatingDecorator.apply(info);
      }
    }
  }

  @Nullable
  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (PlatformDataKeys.TOOL_WINDOW.is(dataId)) {
      return toolWindow;
    }
    return null;
  }

  @Override
  protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
    if (condition == WHEN_ANCESTOR_OF_FOCUSED_COMPONENT && pressed) {
      Collection<KeyStroke> keyStrokes = KeymapUtil.getKeyStrokes(ActionManager.getInstance().getAction("FocusEditor").getShortcutSet());
      if (keyStrokes.contains(ks)) {
        toolWindow.getToolWindowManager().activateEditorComponent();
        return true;
      }
    }
    return super.processKeyBinding(ks, e, condition, pressed);
  }

  public void setTitleActions(@NotNull AnAction[] actions) {
    header.setAdditionalTitleActions(actions);
  }

  void setTabActions(@NotNull AnAction[] actions) {
    header.setTabActions(actions);
  }

  private static final class InnerPanelBorder implements Border {
    @NotNull
    private final ToolWindowImpl window;

    private InnerPanelBorder(@NotNull ToolWindowImpl window) {
      this.window = window;
    }

    @Override
    public void paintBorder(@NotNull Component c, @NotNull Graphics g, int x, int y, int width, int height) {
      g.setColor(JBColor.border());
      doPaintBorder(c, g, x, y, width, height);
    }

    private void doPaintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      Insets insets = getBorderInsets(c);

      Graphics2D graphics2D = (Graphics2D)g;
      if (insets.top > 0) {
        LinePainter2D.paint(graphics2D, x, y + insets.top - 1, x + width - 1, y + insets.top - 1);
        LinePainter2D.paint(graphics2D, x, y + insets.top, x + width - 1, y + insets.top);
      }

      if (insets.left > 0) {
        LinePainter2D.paint(graphics2D, x, y, x, y + height);
        LinePainter2D.paint(graphics2D, x + 1, y, x + 1, y + height);
      }

      if (insets.right > 0) {
        LinePainter2D.paint(graphics2D, x + width - 1, y + insets.top, x + width - 1, y + height);
        LinePainter2D.paint(graphics2D, x + width, y + insets.top, x + width, y + height);
      }

      if (insets.bottom > 0) {
        LinePainter2D.paint(graphics2D, x, y + height - 1, x + width, y + height - 1);
        LinePainter2D.paint(graphics2D, x, y + height, x + width, y + height);
      }
    }

    @Override
    public Insets getBorderInsets(@NotNull Component c) {
      ToolWindowManagerImpl toolWindowManager = window.getToolWindowManager();
      WindowInfo windowInfo = window.getWindowInfo();
      if (toolWindowManager.getProject().isDisposed() ||
          !toolWindowManager.isToolWindowRegistered(window.getId()) || windowInfo.getType() == ToolWindowType.FLOATING || windowInfo.getType() == ToolWindowType.WINDOWED) {
        return JBUI.emptyInsets();
      }

      ToolWindowAnchor anchor = windowInfo.getAnchor();
      Component component = window.getComponent();
      Container parent = component.getParent();
      boolean isSplitter = false;
      boolean isFirstInSplitter = false;
      boolean isVerticalSplitter = false;
      while(parent != null) {
        if (parent instanceof Splitter) {
          Splitter splitter = (Splitter)parent;
          isSplitter = true;
          isFirstInSplitter = splitter.getFirstComponent() == component;
          isVerticalSplitter = splitter.isVertical();
          break;
        }
        component = parent;
        parent = component.getParent();
      }

      int top = isSplitter && (anchor == ToolWindowAnchor.RIGHT || anchor == ToolWindowAnchor.LEFT) && windowInfo.isSplit() && isVerticalSplitter ? -1 : 0;
      int left = anchor == ToolWindowAnchor.RIGHT && (!isSplitter || isVerticalSplitter || isFirstInSplitter) ? 1 : 0;
      int bottom = 0;
      int right = anchor == ToolWindowAnchor.LEFT && (!isSplitter || isVerticalSplitter || !isFirstInSplitter) ? 1 : 0;
      //noinspection UseDPIAwareInsets
      return new Insets(top, left, bottom, right);
    }

    @Override
    public boolean isBorderOpaque() {
      return false;
    }
  }

  /**
   * @return tool window associated with the decorator.
   */
  @NotNull
  final ToolWindowImpl getToolWindow() {
    return toolWindow;
  }

  public int getHeaderHeight() {
    return header.getPreferredSize().height;
  }

  public void setHeaderVisible(boolean value) {
    header.setVisible(value);
  }

  @Override
  public void addNotify() {
    super.addNotify();

    glassPane = IdeGlassPaneUtil.find(this);
    disposable = Disposer.newDisposable();
    glassPane.addMouseMotionPreprocessor(myListener, disposable);
    glassPane.addMousePreprocessor(myListener, disposable);
  }

  @Override
  public void removeNotify() {
    super.removeNotify();

    Disposable disposable = this.disposable;
    if (disposable != null && !Disposer.isDisposed(disposable)) {
      this.disposable = null;
      Disposer.dispose(disposable);
    }
  }

  private final class MyMouseListener extends MouseAdapter {
    private boolean isInDragZone(@NotNull MouseEvent e) {
      Point point = new Point(e.getPoint());
      SwingUtilities.convertPointToScreen(point, e.getComponent());
      if ((toolWindow.getWindowInfo().getAnchor().isHorizontal() ? point.y : point.x) == 0) {
        return false;
      }

      SwingUtilities.convertPointFromScreen(point, divider);
      return Math.abs(toolWindow.getWindowInfo().getAnchor().isHorizontal() ? point.y : point.x) < 6;
    }

    private void updateCursor(@NotNull MouseEvent event, boolean isInDragZone) {
      if (isInDragZone) {
        glassPane.setCursor(divider.getCursor(), divider);
        event.consume();
      }
    }

    @Override
    public void mousePressed(@NotNull MouseEvent e) {
      isDragging = isInDragZone(e);
      updateCursor(e, isDragging);
    }

    @Override
    public void mouseClicked(@NotNull MouseEvent e) {
      updateCursor(e, isInDragZone(e));
    }

    @Override
    public void mouseReleased(@NotNull MouseEvent e) {
      updateCursor(e, isInDragZone(e));
      isDragging = false;
    }

    @Override
    public void mouseMoved(@NotNull MouseEvent e) {
      updateCursor(e, isDragging || isInDragZone(e));
    }

    @Override
    public void mouseDragged(@NotNull MouseEvent e) {
      if (!isDragging) {
        return;
      }

      ToolWindowAnchor anchor = toolWindow.getAnchor();
      Container windowPane = InternalDecorator.this.getParent();
      Point lastPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), windowPane);
      lastPoint.x = Math.min(Math.max(lastPoint.x, 0), windowPane.getWidth());
      lastPoint.y = Math.min(Math.max(lastPoint.y, 0), windowPane.getHeight());

      Rectangle bounds = InternalDecorator.this.getBounds();
      if (anchor == ToolWindowAnchor.TOP) {
        InternalDecorator.this.setBounds(0, 0, bounds.width, lastPoint.y);
      }
      else if (anchor == ToolWindowAnchor.LEFT) {
        InternalDecorator.this.setBounds(0, 0, lastPoint.x, bounds.height);
      }
      else if (anchor == ToolWindowAnchor.BOTTOM) {
        InternalDecorator.this.setBounds(0, lastPoint.y, bounds.width, windowPane.getHeight() - lastPoint.y);
      }
      else if (anchor == ToolWindowAnchor.RIGHT) {
        InternalDecorator.this.setBounds(lastPoint.x, 0, windowPane.getWidth() - lastPoint.x, bounds.height);
      }
      InternalDecorator.this.validate();
      e.consume();
    }
  }

  @Override
  public void putInfo(@NotNull Map<String, String> info) {
    info.put("toolWindowTitle", toolWindow.getTitle());

    Content selection = toolWindow.getContentManager().getSelectedContent();
    if (selection != null) {
      info.put("toolWindowTab", selection.getTabName());
    }
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleInternalDecorator();
    }
    return accessibleContext;
  }

  private final class AccessibleInternalDecorator extends AccessibleJPanel {
    @Override
    public String getAccessibleName() {
      String name = super.getAccessibleName();
      if (name == null) {
        String title = StringUtil.defaultIfEmpty(toolWindow.getTitle(), toolWindow.getStripeTitle());
        title = StringUtil.defaultIfEmpty(title, toolWindow.getId());
        name = StringUtil.notNullize(title) + " Tool Window";
      }
      return name;
    }
  }

  /**
   * Installs a focus traversal policy for the tool window.
   * If the policy cannot handle a keystroke, it delegates the handling to
   * the nearest ancestors focus traversal policy. For instance,
   * this policy does not handle KeyEvent.VK_ESCAPE, so it can delegate the handling
   * to a ThreeComponentSplitter instance.
   */
  static void installFocusTraversalPolicy(@NotNull Container container, @NotNull FocusTraversalPolicy policy) {
    container.setFocusCycleRoot(true);
    container.setFocusTraversalPolicyProvider(true);
    container.setFocusTraversalPolicy(policy);
    installDefaultFocusTraversalKeys(container, KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS);
    installDefaultFocusTraversalKeys(container, KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS);
  }

  private static void installDefaultFocusTraversalKeys(@NotNull Container container, int id) {
    container.setFocusTraversalKeys(id, KeyboardFocusManager.getCurrentKeyboardFocusManager().getDefaultFocusTraversalKeys(id));
  }
}
