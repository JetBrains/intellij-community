// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.CheckedDisposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.WindowInfo;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.*;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.ui.content.impl.ContentImpl;
import com.intellij.ui.content.impl.ContentManagerImpl;
import com.intellij.ui.hover.HoverStateListener;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.util.MathUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.animation.AlphaAnimated;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
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
import java.util.List;
import java.util.*;

import static com.intellij.ui.ExperimentalUI.isNewUI;
import static javax.swing.SwingConstants.*;

public final class InternalDecoratorImpl extends InternalDecorator implements Queryable, DataProvider, ComponentWithMnemonics {
  @ApiStatus.Internal
  public static final Key<Boolean> SHARED_ACCESS_KEY = Key.create("sharedAccess");
  @ApiStatus.Internal
  static final Key<Boolean> HIDE_COMMON_TOOLWINDOW_BUTTONS = Key.create("HideCommonToolWindowButtons");
  static final Key<Boolean> INACTIVE_LOOK = Key.create("InactiveLook");
  public enum Mode {
    SINGLE, VERTICAL_SPLIT, HORIZONTAL_SPLIT, CELL;

    public boolean isSplit() {
      return this == VERTICAL_SPLIT || this == HORIZONTAL_SPLIT;
    }
  }

  private final ToolWindowContentUi contentUi;
  private final JComponent myDecoratorChild;
  private Mode myMode = null;
  private boolean isSplitUnsplitInProgress;
  private boolean isWindowHovered;
  private final ToolWindowImpl toolWindow;

  @Nullable
  private JPanel divider;

  private final JPanel dividerAndHeader = new JPanel(new BorderLayout());

  private CheckedDisposable disposable;

  /**
   * Catches all event from tool window and modifies decorator's appearance.
   */
  static final String HIDE_ACTIVE_WINDOW_ACTION_ID = "HideActiveWindow";

  private final ToolWindowHeader header;
  private final Wrapper notificationHeader = new Wrapper();
  private InternalDecoratorImpl myFirstDecorator;
  private InternalDecoratorImpl mySecondDecorator;
  private Splitter mySplitter;

  InternalDecoratorImpl(@NotNull ToolWindowImpl toolWindow, @NotNull ToolWindowContentUi contentUi, @NotNull JComponent decoratorChild) {
    this.contentUi = contentUi;
    myDecoratorChild = decoratorChild;
    this.toolWindow = toolWindow;

    setFocusable(false);
    setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
    updateMode(Mode.SINGLE);

    header = new ToolWindowHeader(toolWindow, contentUi, () -> toolWindow.createPopupGroup(true)) {
      @Override
      protected boolean isActive() {
        return toolWindow.isActive() && Boolean.TRUE != ClientProperty.get(InternalDecoratorImpl.this, INACTIVE_LOOK) &&
               !ExperimentalUI.isNewToolWindowsStripes();
      }

      @Override
      protected void hideToolWindow() {
        toolWindow.getToolWindowManager().hideToolWindow(toolWindow.getId(), false, true, false, ToolWindowEventSource.HideButton);
      }
    };

    enableEvents(AWTEvent.COMPONENT_EVENT_MASK);

    installFocusTraversalPolicy(this, new LayoutFocusTraversalPolicy());

    dividerAndHeader.setOpaque(false);
    dividerAndHeader.add(JBUI.Panels.simplePanel(header).addToBottom(notificationHeader), BorderLayout.SOUTH);

    if (SystemInfo.isMac) {
      setBackground(new JBColor(Gray._200, Gray._90));
    }

    if (ExperimentalUI.isNewToolWindowsStripes()) {
      setBackground(JBUI.CurrentTheme.ToolWindow.background());
    }

    getContentManager().addContentManagerListener(new ContentManagerListener() {
      @Override
      public void contentRemoved(@NotNull ContentManagerEvent event) {
        InternalDecoratorImpl parentDecorator = findNearestDecorator(InternalDecoratorImpl.this);
        if (parentDecorator == null) return;
        if (!parentDecorator.isSplitUnsplitInProgress() && !isSplitUnsplitInProgress() && getContentManager().isEmpty()) {
          parentDecorator.unsplit(null);
        }
      }
    });
  }

  public void updateMode(Mode mode) {
    if (mode == myMode) return;
    myMode = mode;
    removeAll();
    setBorder(null);
    switch (mode) {
      case SINGLE:
      case CELL: {
        setLayout(new BorderLayout());
        add(dividerAndHeader, BorderLayout.NORTH);
        add(myDecoratorChild, BorderLayout.CENTER);
        ApplicationManager.getApplication().invokeLater(() -> setBorder(new InnerPanelBorder(toolWindow)));
        ObjectUtils.consumeIfNotNull(myFirstDecorator, decorator -> Disposer.dispose(decorator.getContentManager()));
        ObjectUtils.consumeIfNotNull(mySecondDecorator, decorator -> Disposer.dispose(decorator.getContentManager()));
        return;
      }
      case VERTICAL_SPLIT:
      case HORIZONTAL_SPLIT: {
        mySplitter = new OnePixelSplitter(mode == Mode.VERTICAL_SPLIT);
        mySplitter.setFirstComponent(myFirstDecorator);
        mySplitter.setSecondComponent(mySecondDecorator);

        setLayout(new BorderLayout());
        add(mySplitter, BorderLayout.CENTER);
      }
    }
  }

  public void splitWithContent(@NotNull Content content, @MagicConstant(intValues = {CENTER, TOP, LEFT, BOTTOM, RIGHT, -1}) int dropSide,
                               int dropIndex) {
    if (dropSide == -1 || dropSide == CENTER || dropIndex >= 0) {
      getContentManager().addContent(content, dropIndex);
      return;
    }
    myFirstDecorator = toolWindow.createCellDecorator();
    attach(myFirstDecorator);
    mySecondDecorator = toolWindow.createCellDecorator();
    attach(mySecondDecorator);

    ArrayList<Content> contents = ContainerUtil.newArrayList(getContentManager().getContents());
    if (!contents.contains(content)) contents.add(content);
    for (Content c : contents) {
      moveContent(c, this, (c != content ^ (dropSide == LEFT || dropSide == TOP) ? myFirstDecorator : mySecondDecorator));
    }

    myFirstDecorator.updateMode(Mode.CELL);
    mySecondDecorator.updateMode(Mode.CELL);
    updateMode(dropSide == TOP || dropSide == BOTTOM ? Mode.VERTICAL_SPLIT : Mode.HORIZONTAL_SPLIT);
  }

  private static void moveContent(@NotNull Content content, @NotNull InternalDecoratorImpl source, @NotNull InternalDecoratorImpl target) {
    ContentManager targetContentManager = target.getContentManager();
    if (Objects.equals(content.getManager(), targetContentManager)) return;
    Boolean initialState = content.getUserData(Content.TEMPORARY_REMOVED_KEY);
    try {
      source.setSplitUnsplitInProgress(true);
      content.putUserData(Content.TEMPORARY_REMOVED_KEY, Boolean.TRUE);
      ObjectUtils.consumeIfNotNull(content.getManager(), manager -> manager.removeContent(content, false));
      ((ContentImpl)content).setManager(targetContentManager);
      targetContentManager.addContent(content);
    } finally {
      content.putUserData(Content.TEMPORARY_REMOVED_KEY, initialState);
      source.setSplitUnsplitInProgress(false);
    }
  }

  private void raise(boolean raiseFirst) {
    @NotNull InternalDecoratorImpl source = raiseFirst ? myFirstDecorator : mySecondDecorator;

    final InternalDecoratorImpl first = source.myFirstDecorator;
    final InternalDecoratorImpl second = source.mySecondDecorator;
    final Mode mode = source.myMode;

    source.detach(first);
    source.detach(second);
    source.myFirstDecorator = null;
    source.mySecondDecorator = null;


    InternalDecoratorImpl toRemove1 = myFirstDecorator;
    InternalDecoratorImpl toRemove2= mySecondDecorator;
    toRemove1.updateMode(Mode.CELL);
    toRemove2.updateMode(Mode.CELL);

    first.setSplitUnsplitInProgress(true);
    second.setSplitUnsplitInProgress(true);
    try {
      myFirstDecorator = first;
      mySecondDecorator = second;
      myMode = mode;//Previous mode is split too
      mySplitter.setOrientation(mode == Mode.VERTICAL_SPLIT);
      mySplitter.setFirstComponent(myFirstDecorator);
      mySplitter.setSecondComponent(mySecondDecorator);
      attach(first);
      attach(second);
    } finally {
      first.setSplitUnsplitInProgress(false);
      second.setSplitUnsplitInProgress(false);

      Disposer.dispose(toRemove1.getContentManager());
      Disposer.dispose(toRemove2.getContentManager());
    }
  }

  private void detach(InternalDecoratorImpl decorator) {
    ContentManager parentManager = getContentManager();
    ContentManager childManager = decorator.getContentManager();
    if (parentManager instanceof ContentManagerImpl && childManager instanceof ContentManagerImpl) {
      ((ContentManagerImpl)parentManager).removeNestedManager((ContentManagerImpl)childManager);
    }
  }

  private void attach(InternalDecoratorImpl decorator) {
    ContentManager parentManager = getContentManager();
    ContentManager childManager = decorator.getContentManager();
    if (parentManager instanceof ContentManagerImpl && childManager instanceof ContentManagerImpl) {
      ((ContentManagerImpl)parentManager).addNestedManager((ContentManagerImpl)childManager);
    }
  }

  public boolean canUnsplit() {
    if (getMode() != InternalDecoratorImpl.Mode.CELL) return false;
    InternalDecoratorImpl parent = findNearestDecorator(this);
    if (parent != null) {
      if (parent.myFirstDecorator == this) {
        return parent.mySecondDecorator != null && parent.mySecondDecorator.getMode() == Mode.CELL;
      }
      if (parent.mySecondDecorator == this) {
        return parent.myFirstDecorator != null && parent.myFirstDecorator.getMode() == Mode.CELL;
      }
    }
    return false;
  }

  public void unsplit(@Nullable Content toSelect) {
    if (!myMode.isSplit()) {
      ObjectUtils.consumeIfNotNull(findNearestDecorator(this), decorator -> decorator.unsplit(toSelect));
      return;
    }
    if (isSplitUnsplitInProgress()) {
      return;
    }
    setSplitUnsplitInProgress(true);
    try {
      if (myFirstDecorator == null || mySecondDecorator == null) return;
      if (myFirstDecorator.getMode().isSplit()) {
        raise(true);
        return;
      }
      if (mySecondDecorator.getMode().isSplit()) {
        raise(false);
        return;
      }
      for (Content c : myFirstDecorator.getContentManager().getContents()) {
        moveContent(c, myFirstDecorator, this);
      }
      for (Content c : mySecondDecorator.getContentManager().getContents()) {
        moveContent(c, mySecondDecorator, this);
      }
      updateMode(findNearestDecorator(this) != null ? Mode.CELL : Mode.SINGLE);
      if (toSelect != null) {
        ObjectUtils.consumeIfNotNull(toSelect.getManager(), m -> m.setSelectedContent(toSelect));
      }
      myFirstDecorator = null;
      mySecondDecorator = null;
      mySplitter = null;
    }
    finally {
      setSplitUnsplitInProgress(false);
    }
  }


  public void setSplitUnsplitInProgress(boolean inProgress) {
    isSplitUnsplitInProgress = inProgress;
  }

  @Override
  public boolean isSplitUnsplitInProgress() {
    return isSplitUnsplitInProgress;
  }

  public void setMode(Mode mode) {
    myMode = mode;
  }

  public Mode getMode() {
    return myMode;
  }

  @Override
  public ContentManager getContentManager() {
    return contentUi.getContentManager();
  }

  @Override
  public ActionToolbar getHeaderToolbar() {
    return header.getToolbar();
  }

  public ActionGroup getHeaderToolbarActions() {
    return header.getToolbarActions();
  }

  public ActionGroup getHeaderToolbarWestActions() {
    return header.getToolbarWestActions();
  }

  @Override
  public String toString() {
    return toolWindow.getId() + ": " + StringUtil.trimMiddle(Arrays.toString(Arrays.stream(getContentManager().getContents()).map(
      content -> content.getDisplayName()).toArray()), 40) + " #"+System.identityHashCode(this);
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
        return JBInsets.emptyInsets();
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
  @NotNull ToolWindowImpl getToolWindow() {
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

  void updateActiveAndHoverState() {
    ActionToolbar toolbar = getHeaderToolbar();
    if (toolbar instanceof AlphaAnimated) {
      AlphaAnimated alpha = (AlphaAnimated)toolbar;
      alpha.getAlphaAnimator().setVisible(!isNewUI() || isWindowHovered || header.isPopupShowing() || toolWindow.isActive());
    }
  }

  public void activate(ToolWindowEventSource source) {
    toolWindow.fireActivated(source);
  }

  @NotNull
  public String getToolWindowId() {
    return toolWindow.getId();
  }

  public void setHeaderComponent(@Nullable JComponent notification) {
    notificationHeader.setContent(notification);
  }

  @Nullable
  public JComponent getHeaderComponent() {
    JComponent component = notificationHeader.getTargetComponent();
    return component != notificationHeader ? component : null;
  }

  @Nullable
  public Rectangle getHeaderScreenBounds() {
    if (!header.isShowing()) return null;
    Rectangle bounds = header.getBounds();
    bounds.setLocation(header.getLocationOnScreen());
    return bounds;
  }

  @Override
  public void addNotify() {
    super.addNotify();

    if (isSplitUnsplitInProgress())  {
      return;
    }

    if (disposable != null) {
      Disposer.dispose(disposable);
    }
    JPanel divider = this.divider;
    disposable = Disposer.newCheckedDisposable();
    HOVER_STATE_LISTENER.addTo(this, disposable);
    updateActiveAndHoverState();
    if (divider != null) {
      IdeGlassPane glassPane = (IdeGlassPane)getRootPane().getGlassPane();
      ResizeOrMoveDocketToolWindowMouseListener listener = new ResizeOrMoveDocketToolWindowMouseListener(divider, glassPane, this);
      glassPane.addMouseMotionPreprocessor(listener, disposable);
      glassPane.addMousePreprocessor(listener, disposable);
    }
    contentUi.update();
  }

  @Override
  public void removeNotify() {
    super.removeNotify();

    if (isSplitUnsplitInProgress()) {
      return;
    }

    CheckedDisposable disposable = this.disposable;
    if (disposable != null && !disposable.isDisposed()) {
      this.disposable = null;
      Disposer.dispose(disposable);
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  public void reshape(int x, int y, int w, int h) {
    super.reshape(x, y, w, h);
    InternalDecoratorImpl topLevelDecorator = findTopLevelDecorator(this);
    if (topLevelDecorator == null || !topLevelDecorator.isShowing()) {
      putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, null);
      putClientProperty(HIDE_COMMON_TOOLWINDOW_BUTTONS, null);
      putClientProperty(INACTIVE_LOOK, null);
    }
    else {
      Object hideLabel = SwingUtilities.convertPoint(this, x, y, topLevelDecorator).equals(new Point()) ? null : "true";
      putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, hideLabel);
      Point topScreenLocation = topLevelDecorator.getLocationOnScreen();
      topScreenLocation.x += topLevelDecorator.getWidth();
      Point screenLocation = getLocationOnScreen();
      screenLocation.x += w;
      Boolean hideButtons = topScreenLocation.equals(screenLocation) ? null : Boolean.TRUE;
      Boolean hideActivity = topScreenLocation.y == screenLocation.y ? null : Boolean.TRUE;
      putClientProperty(HIDE_COMMON_TOOLWINDOW_BUTTONS, hideButtons);
      putClientProperty(INACTIVE_LOOK, hideActivity);
    }
    contentUi.update();
  }

  public void setDropInfoIndex(int index, int width) {
    contentUi.setDropInfoIndex(index, width);
  }

  public void updateBounds(@NotNull MouseEvent dragEvent) {
    //"Undock" mode only, for "Dock" mode processing see com.intellij.openapi.wm.impl.content.ToolWindowContentUi.initMouseListeners
    ToolWindowAnchor anchor = toolWindow.getAnchor();
    Container windowPane = getParent();
    Point lastPoint = SwingUtilities.convertPoint(dragEvent.getComponent(), dragEvent.getPoint(), windowPane);
    lastPoint.x = MathUtil.clamp(lastPoint.x, 0, windowPane.getWidth());
    lastPoint.y = MathUtil.clamp(lastPoint.y, 0, windowPane.getHeight());

    Rectangle bounds = getBounds();
    if (anchor == ToolWindowAnchor.TOP) {
      setBounds(0, 0, bounds.width, lastPoint.y);
    }
    else if (anchor == ToolWindowAnchor.LEFT) {
      setBounds(0, 0, lastPoint.x, bounds.height);
    }
    else if (anchor == ToolWindowAnchor.BOTTOM) {
      setBounds(0, lastPoint.y, bounds.width, windowPane.getHeight() - lastPoint.y);
    }
    else if (anchor == ToolWindowAnchor.RIGHT) {
      setBounds(lastPoint.x, 0, windowPane.getWidth() - lastPoint.x, bounds.height);
    }
    validate();
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
      return Math.abs(decorator.toolWindow.getWindowInfo().getAnchor().isHorizontal() ? point.y : point.x) <= ToolWindowsPane.getHeaderResizeArea();
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
      decorator.updateBounds(e);
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

  @Nullable
  public static InternalDecoratorImpl findTopLevelDecorator(Component component) {
    Component parent = component != null ? component.getParent() : null;
    InternalDecoratorImpl candidate = null;
    while (parent != null) {
      if (parent instanceof InternalDecoratorImpl) candidate = (InternalDecoratorImpl)parent;
      parent = parent.getParent();
    }
    return candidate;
  }

  public static InternalDecoratorImpl findNearestDecorator(Component component) {
    if (component != null) component = component.getParent();
    return (InternalDecoratorImpl)ComponentUtil.findParentByCondition(component, c -> c instanceof InternalDecoratorImpl);
  }

  private static void installDefaultFocusTraversalKeys(@NotNull Container container, int id) {
    container.setFocusTraversalKeys(id, KeyboardFocusManager.getCurrentKeyboardFocusManager().getDefaultFocusTraversalKeys(id));
  }

  private static final HoverStateListener HOVER_STATE_LISTENER = new HoverStateListener() {
    @Override
    protected void hoverChanged(@NotNull Component component, boolean hovered) {
      if (component instanceof InternalDecoratorImpl) {
        InternalDecoratorImpl decorator = (InternalDecoratorImpl)component;
        decorator.isWindowHovered = hovered;
        decorator.updateActiveAndHoverState();
      }
    }
  };

  public ToolWindowHeader getHeader() {
    return header;
  }
}
