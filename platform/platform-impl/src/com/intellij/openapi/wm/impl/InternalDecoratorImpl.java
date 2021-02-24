// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.WindowInfo;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.ComponentWithMnemonics;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.content.Content;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.util.MathUtil;
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
import java.util.List;
import java.util.Map;

public final class InternalDecoratorImpl extends InternalDecorator implements Queryable, DataProvider, ComponentWithMnemonics {
  private final ToolWindowImpl toolWindow;

  @Nullable
  private JPanel divider;

  private final JPanel dividerAndHeader = new JPanel(new BorderLayout());

  private Disposable disposable;

  /**
   * Catches all event from tool window and modifies decorator's appearance.
   */
  static final String HIDE_ACTIVE_WINDOW_ACTION_ID = "HideActiveWindow";

  private final ToolWindowHeader header;

  InternalDecoratorImpl(@NotNull ToolWindowImpl toolWindow, @NotNull ToolWindowContentUi contentUi, @NotNull JComponent decoratorChild) {
    setLayout(new BorderLayout());

    this.toolWindow = toolWindow;

    setFocusable(false);
    setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());

    header = new ToolWindowHeader(toolWindow, contentUi, () -> toolWindow.createPopupGroup(true)) {
      @Override
      protected boolean isActive() {
        return toolWindow.isActive();
      }

      @Override
      protected void hideToolWindow() {
        toolWindow.getToolWindowManager().hideToolWindow(toolWindow.getId(), false, true, ToolWindowEventSource.HideButton);
      }
    };

    enableEvents(AWTEvent.COMPONENT_EVENT_MASK);

    installFocusTraversalPolicy(this, new LayoutFocusTraversalPolicy());

    dividerAndHeader.setOpaque(false);
    dividerAndHeader.add(header, BorderLayout.SOUTH);
    add(dividerAndHeader, BorderLayout.NORTH);
    if (SystemInfo.isMac) {
      setBackground(new JBColor(Gray._200, Gray._90));
    }

    add(decoratorChild, BorderLayout.CENTER);

    setBorder(new InnerPanelBorder(toolWindow));
  }

  @Override
  public ActionToolbar getHeaderToolbar() {
    return header.getToolbar();
  }

  @Override
  public void setBounds(int x, int y, int width, int height) {
    super.setBounds(x, y, width, height);
  }

  @Override
  public String toString() {
    return toolWindow.getId();
  }

  @NotNull
  private JComponent initDivider() {
    if (divider != null) {
      return divider;
    }

    divider = new JPanel() {
      @NotNull
      @Override
      public Cursor getCursor() {
        WindowInfo info = toolWindow.getWindowInfo();
        boolean isVerticalCursor = info.getType() == ToolWindowType.DOCKED ? info.getAnchor().isSplitVertically() : info.getAnchor().isHorizontal();
        return isVerticalCursor ? Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR) : Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
      }
    };
    return divider;
  }

  void applyWindowInfo(@NotNull WindowInfo info) {
    if (info.getType() == ToolWindowType.SLIDING) {
      ToolWindowAnchor anchor = info.getAnchor();
      JComponent divider = initDivider();
      divider.invalidate();
      if (anchor == ToolWindowAnchor.TOP) {
        add(divider, BorderLayout.SOUTH);
      }
      else if (anchor == ToolWindowAnchor.LEFT) {
        add(divider, BorderLayout.EAST);
      }
      else if (anchor == ToolWindowAnchor.BOTTOM) {
        dividerAndHeader.add(divider, BorderLayout.NORTH);
      }
      else if (anchor == ToolWindowAnchor.RIGHT) {
        add(divider, BorderLayout.WEST);
      }
      divider.setPreferredSize(new Dimension(0, 0));
    }
    else if (divider != null) {
      // docked and floating windows don't have divider
      divider.getParent().remove(divider);
      divider = null;
    }

    // push "apply" request forward
    if (info.getType() == ToolWindowType.FLOATING) {
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

  public void setTitleActions(@NotNull List<? extends AnAction> actions) {
    header.setAdditionalTitleActions(actions);
  }

  void setTabActions(@NotNull AnAction @NotNull [] actions) {
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
          !toolWindowManager.isToolWindowRegistered(window.getId()) ||
          window.isDisposed() ||
          windowInfo.getType() == ToolWindowType.FLOATING ||
          windowInfo.getType() == ToolWindowType.WINDOWED) {
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

  @Override
  public int getHeaderHeight() {
    return header.getPreferredSize().height;
  }

  @Override
  public void setHeaderVisible(boolean value) {
    header.setVisible(value);
  }

  @Override
  public boolean isHeaderVisible() {
    return header.isVisible();
  }

  public boolean isActive() {
    return toolWindow.isActive();
  }

  public void activate(ToolWindowEventSource source) {
    toolWindow.fireActivated(source);
  }

  @NotNull
  public String getToolWindowId() {
    return toolWindow.getId();
  }

  @Override
  public void addNotify() {
    super.addNotify();

    JPanel divider = this.divider;
    if (divider != null) {
      IdeGlassPane glassPane = (IdeGlassPane)getRootPane().getGlassPane();
      if (disposable != null) {
        Disposer.dispose(disposable);
      }
      disposable = Disposer.newDisposable();
      ResizeOrMoveDocketToolWindowMouseListener listener = new ResizeOrMoveDocketToolWindowMouseListener(divider, glassPane, this);
      glassPane.addMouseMotionPreprocessor(listener, disposable);
      glassPane.addMousePreprocessor(listener, disposable);
    }
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

  private static final class ResizeOrMoveDocketToolWindowMouseListener extends MouseAdapter {
    private final JComponent divider;
    private final IdeGlassPane glassPane;
    private final InternalDecoratorImpl decorator;
    private boolean isDragging;

    private ResizeOrMoveDocketToolWindowMouseListener(@NotNull JComponent divider, @NotNull IdeGlassPane glassPane, @NotNull InternalDecoratorImpl decorator) {
      this.divider = divider;
      this.glassPane = glassPane;
      this.decorator = decorator;
    }

    private boolean isInDragZone(@NotNull MouseEvent e) {
      Point point = new Point(e.getPoint());
      SwingUtilities.convertPointToScreen(point, e.getComponent());
      if ((decorator.toolWindow.getWindowInfo().getAnchor().isHorizontal() ? point.y : point.x) == 0) {
        return false;
      }

      SwingUtilities.convertPointFromScreen(point, divider);
      return Math.abs(decorator.toolWindow.getWindowInfo().getAnchor().isHorizontal() ? point.y : point.x) < 6;
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

      ToolWindowAnchor anchor = decorator.toolWindow.getAnchor();
      Container windowPane = decorator.getParent();
      Point lastPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), windowPane);
      lastPoint.x = MathUtil.clamp(lastPoint.x, 0, windowPane.getWidth());
      lastPoint.y = MathUtil.clamp(lastPoint.y, 0, windowPane.getHeight());

      Rectangle bounds = decorator.getBounds();
      if (anchor == ToolWindowAnchor.TOP) {
        decorator.setBounds(0, 0, bounds.width, lastPoint.y);
      }
      else if (anchor == ToolWindowAnchor.LEFT) {
        decorator.setBounds(0, 0, lastPoint.x, bounds.height);
      }
      else if (anchor == ToolWindowAnchor.BOTTOM) {
        decorator.setBounds(0, lastPoint.y, bounds.width, windowPane.getHeight() - lastPoint.y);
      }
      else if (anchor == ToolWindowAnchor.RIGHT) {
        decorator.setBounds(lastPoint.x, 0, windowPane.getWidth() - lastPoint.x, bounds.height);
      }
      decorator.validate();
      e.consume();
    }
  }

  @Override
  public void putInfo(@NotNull Map<? super String, ? super String> info) {
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
        name = StringUtil.notNullize(title) + " " + IdeBundle.message("internal.decorator.accessible.postfix");
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
